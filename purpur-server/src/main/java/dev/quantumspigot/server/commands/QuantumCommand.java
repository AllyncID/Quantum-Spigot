package dev.quantumspigot.server.commands;

import dev.quantumspigot.server.QuantumBuildInfo;
import dev.quantumspigot.server.QuantumRuntime;
import io.papermc.paper.configuration.GlobalConfiguration;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import static dev.quantumspigot.server.commands.QuantumMessages.*;

public final class QuantumCommand extends Command {
    private static final List<String> SUBCOMMANDS = List.of("help", "version", "tps", "mspt", "worlds", "health", "threads", "chunks", "entities", "plugins", "profile", "config", "reload", "gc");
    private final Server server;
    private final QuantumRuntime runtime;

    private QuantumCommand(Server server, QuantumRuntime runtime) {
        super("quantum", "QuantumSpigot diagnostics", "/quantum <" + String.join("|", SUBCOMMANDS) + ">", List.of("qspigot", "qs"));
        this.server = server;
        this.runtime = runtime;
        this.setPermission(String.join(";", SUBCOMMANDS.stream().map(name -> "quantum.command." + name).toList()));
    }

    public static void register(Server server, QuantumRuntime runtime) {
        Map<String, Boolean> children = new LinkedHashMap<>();
        for (String name : SUBCOMMANDS) {
            String permission = "quantum.command." + name;
            if (server.getPluginManager().getPermission(permission) == null) {
                server.getPluginManager().addPermission(new Permission(permission, "Read Quantum " + name + " diagnostics", PermissionDefault.OP));
            }
            children.put(permission, true);
        }
        if (server.getPluginManager().getPermission("quantum.admin") == null) {
            server.getPluginManager().addPermission(new Permission("quantum.admin", "All Quantum command permissions", PermissionDefault.OP, children));
        }
        server.getCommandMap().register("quantumspigot", new QuantumCommand(server, runtime));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        String subcommand = args.length == 0 ? "health" : args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(subcommand)) {
            this.send(sender, header("Unknown command").append(warning(" — use /quantum help")));
            return true;
        }
        if (!sender.hasPermission("quantum.command." + subcommand)) {
            this.send(sender, header("Permission required").append(warning(" — quantum.command." + subcommand)));
            return true;
        }
        if (Thread.currentThread() != this.runtime.mainThread()) {
            this.send(sender, header("Unavailable").append(warning(" — run diagnostics on the server thread.")));
            return true;
        }
        this.send(sender, header(subcommand.toUpperCase(Locale.ROOT)));
        switch (subcommand) {
            case "help" -> {
                for (String name : SUBCOMMANDS) if (sender.hasPermission("quantum.command." + name)) {
                    this.line(sender, Component.text("/quantum " + name, NamedTextColor.AQUA).clickEvent(ClickEvent.suggestCommand("/quantum " + name)));
                }
                this.line(sender, note("More: mspt histogram | threads dump. Click a command to fill chat."));
            }
            case "version" -> {
                this.line(sender, value(this.server.getVersion()));
                this.line(sender, pair("Upstream", QuantumBuildInfo.upstream()));
                this.line(sender, pair("Java", Runtime.version()));
                this.profile(sender);
            }
            case "tps" -> { this.tps(sender); this.mspt(sender, 60); }
            case "worlds" -> {
                if (!this.runtime.config().diagnostics().enabled() || !this.runtime.config().diagnostics().worldTimings()) {
                    this.line(sender, note("Enable diagnostics.world-timings and restart to measure world ticks."));
                } else for (var world : this.server.getWorlds()) {
                    var history = ((CraftWorld) world).getHandle().quantumTickHistory;
                    if (history == null) continue;
                    var sample = history.snapshot(System.nanoTime(), 60);
                    this.line(sender, pair(world.getKey().asString(), "avg " + decimal(sample.average()) + " | p95 " + decimal(sample.p95())
                        + " | p99 " + decimal(sample.p99()) + " | max " + decimal(sample.max()) + " ms"));
                    this.line(sender, note(sample.samples() + " world ticks; " + decimal(sample.coverageSeconds()) + "s coverage"));
                }
                this.line(sender, note("World tick wall time only; excludes global scheduler/network. Do not sum world percentiles."));
            }
            case "mspt" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("histogram")) this.histogram(sender);
                else for (int seconds : new int[] {60, 300, 900}) this.mspt(sender, seconds);
            }
            case "health" -> {
                var sample = this.runtime.ticks(60);
                String status = !this.runtime.config().diagnostics().enabled() ? "DIAGNOSTICS DISABLED"
                    : sample.samples() == 0 ? "WARMING UP" : sample.p95() >= 75 ? "CRITICAL"
                    : sample.p95() >= 50 ? "OVERLOADED" : sample.p95() >= 40 ? "DEGRADED" : "HEALTHY";
                NamedTextColor color = status.equals("HEALTHY") ? NamedTextColor.GREEN
                    : sample.p95() >= 50 ? NamedTextColor.RED : NamedTextColor.YELLOW;
                this.line(sender, label("Status: ").append(Component.text(status, color)).append(note(" (tick p95)")));
                this.tps(sender);
                this.mspt(sender, 60);
                var counts = this.runtime.counts();
                this.line(sender, pair("Players", counts.players()).append(separator()).append(pair("Chunks", counts.loadedChunks()))
                    .append(separator()).append(pair("Entities", counts.loadedEntities())));
                this.line(sender, pair("Slow tasks", this.measured(this.runtime.pluginStalls())).append(separator())
                    .append(pair("Lag incidents", this.measured(this.runtime.lagIncidents()))));
                this.line(sender, note("Details: /quantum chunks | gc | threads | config"));
            }
            case "threads" -> {
                this.threads(sender);
                if (args.length > 1 && args[1].equalsIgnoreCase("dump")) {
                    Thread.getAllStackTraces().entrySet().stream().sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Thread::getName))).limit(64).forEach(entry -> {
                            this.line(sender, pair(entry.getKey().getName(), entry.getKey().getState()));
                            for (int i = 0; i < Math.min(12, entry.getValue().length); i++) this.line(sender, note("  " + entry.getValue()[i]));
                        });
                    this.line(sender, note("Snapshot limited to 64 threads / 12 frames each."));
                }
            }
            case "chunks" -> {
                this.line(sender, pair("Loaded chunks", this.runtime.counts().loadedChunks()));
                this.line(sender, pair("Packets sent since startup", this.measured(this.runtime.chunksSent())));
                if (this.runtime.config().diagnostics().enabled()) {
                    var q = this.runtime.chunkQueues();
                    this.line(sender, pair("Waiting load", q.waitingLoad()).append(separator()).append(pair("Loading", q.loading())));
                    this.line(sender, pair("Waiting generation", q.waitingGeneration()).append(separator()).append(pair("Generating", q.generating())));
                    this.line(sender, pair("Waiting send", q.waitingSend()).append(separator()).append(pair("Waiting ticking", q.waitingTicking())));
                    this.line(sender, note("Last tick; entries across " + q.sampledPlayers() + " players, not unique chunks."));
                } else this.line(sender, warning("Queue measurements unavailable: diagnostics disabled."));
                for (var world : this.server.getWorlds()) {
                    var work = QuantumRuntime.storageWork((CraftWorld) world);
                    this.line(sender, pair(world.getKey().asString() + " IO tasks chunk/entity/POI", work.chunkTasks() + "/" + work.entityTasks() + "/" + work.poiTasks()));
                    this.line(sender, pair("Autosave scheduled / oldest age", work.autosaveScheduledChunks() + " / " + work.oldestAutosaveAgeTicks() + " ticks"));
                }
                this.line(sender, note("Native IO includes reads/writes in flight. Autosave entries include clean chunks; age is scheduling time, not dirty-data durability."));
            }
            case "entities" -> {
                this.line(sender, pair("Loaded entities", this.runtime.counts().loadedEntities()));
                this.line(sender, pair("Active ticks", this.measured(this.runtime.lastEntityTicks())).append(separator())
                    .append(pair("Inactive ticks", this.measured(this.runtime.lastInactiveEntityTicks()))));
                this.line(sender, note("Last-tick invocations, not unique entities or category timings."));
                this.line(sender, pair("Block entity ticks", this.measured(this.runtime.lastBlockEntityTicks())).append(separator())
                    .append(pair("Sleeping eligible tickers", this.measured(this.runtime.lastSleepingBlockEntities()))));
            }
            case "plugins" -> {
                var stalls = this.runtime.stalls();
                if (!this.runtime.config().diagnostics().enabled()) this.line(sender, warning("Diagnostics disabled; no task measurements."));
                else if (stalls.isEmpty()) this.line(sender, note("No slow synchronous scheduled tasks recorded."));
                stalls.stream().skip(Math.max(0, stalls.size() - 10)).forEach(stall -> {
                    this.line(sender, pair(stall.plugin() + " #" + stall.taskId(), decimal(stall.durationMs()) + " ms"));
                    this.line(sender, note(stall.taskClass()));
                });
                this.line(sender, note("Last 10 incidents; this is not a plugin compatibility verdict."));
                var work = this.runtime.pluginWork();
                if (work == null) this.line(sender, note("Sampled event/command/task attribution disabled in quantum-diagnostics.yml."));
                else {
                    this.line(sender, pair("Sampled roots / observed roots", work.sampledRoots() + " / " + work.roots()));
                    for (var sample : work.top(10)) {
                        this.line(sender, pair(sample.key().plugin() + " " + sample.key().kind(), "self " + decimal(sample.exclusiveMs())
                            + " | inclusive " + decimal(sample.inclusiveMs()) + " ms (" + sample.samples() + " samples)"));
                        this.line(sender, note(sample.key().operation() + " | recent p95 " + decimal(sample.recentP95Ms()) + " ms / " + sample.recentSamples() + " samples"));
                    }
                    this.line(sender, pair("Groups / dropped groups / depth overflow", work.groupCount() + " / " + work.droppedGroups() + " / " + work.depthOverflows()));
                    this.line(sender, note("Sampled totals since startup; self excludes nested covered hooks. No extrapolation. Async/direct NMS and native Brigadier plugin commands are outside coverage."));
                }
            }
            case "profile" -> this.profile(sender);
            case "config" -> this.configuration(sender);
            case "reload" -> this.line(sender, note(this.runtime.reload()));
            case "gc" -> this.gc(sender);
            default -> throw new IllegalStateException(subcommand);
        }
        return true;
    }

    private void send(CommandSender sender, Component message) { sender.sendMessage(style(message, this.runtime.config().coloredCommands())); }
    private void line(CommandSender sender, Component message) { this.send(sender, Component.text("  ").append(message)); }

    private void tps(CommandSender sender) {
        double[] t = this.server.getTPS();
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(String.format(Locale.ROOT, "[Quantum] TPS (upstream) 5s %.2f | 1m %.2f | 5m %.2f | 15m %.2f", t[0], t[1], t[2], t[3]));
        } else this.line(sender, label("TPS  5s ").append(QuantumMessages.tps(t[0])).append(label(" | 1m ")).append(QuantumMessages.tps(t[1]))
            .append(label(" | 5m ")).append(QuantumMessages.tps(t[2])).append(label(" | 15m ")).append(QuantumMessages.tps(t[3])));
    }

    private String measured(long value) { return this.runtime.config().diagnostics().enabled() ? Long.toString(value) : "unavailable"; }

    private void mspt(CommandSender sender, int seconds) {
        var s = this.runtime.ticks(seconds);
        if (s.samples() == 0) {
            this.line(sender, warning("MSPT " + seconds + "s: " + (this.runtime.config().diagnostics().enabled() ? "warming up" : "diagnostics disabled")));
            return;
        }
        // Keep the console telemetry contract stable for the load-test collector.
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(String.format(Locale.ROOT,
                "[Quantum] MSPT %ds: avg %.2f | p50 %.2f | p75 %.2f | p95 %.2f | p99 %.2f | max %.2f | last %.2f (%d samples, %.1fs coverage)",
                seconds, s.average(), s.p50(), s.p75(), s.p95(), s.p99(), s.max(), s.last(), s.samples(), s.coverageSeconds()));
            return;
        }
        this.line(sender, label("MSPT " + seconds + "s  avg ").append(QuantumMessages.mspt(s.average()))
            .append(label(" | p95 ")).append(QuantumMessages.mspt(s.p95())).append(label(" | p99 ")).append(QuantumMessages.mspt(s.p99()))
            .append(label(" | max ")).append(QuantumMessages.mspt(s.max())));
        this.line(sender, label("p50 ").append(QuantumMessages.mspt(s.p50())).append(label(" | p75 ")).append(QuantumMessages.mspt(s.p75()))
            .append(label(" | last ")).append(QuantumMessages.mspt(s.last())));
        this.line(sender, note(String.format(Locale.ROOT, "%d ticks over %.1fs; 50 ms/tick is the 20 TPS budget.", s.samples(), s.coverageSeconds())));
    }

    private void threads(CommandSender sender) {
        this.line(sender, pair("Main", this.runtime.mainThread().getName() + " / " + this.runtime.mainThread().getState()));
        var b = this.runtime.budget();
        this.line(sender, pair("Logical CPUs", b.processors()).append(separator()).append(pair("Quantum budget", b.total()))
            .append(separator()).append(pair("Unallocated", b.unallocated() - (this.runtime.chunkSerializer() == null ? 0 : this.runtime.chunkSerializer().workers()))));
        var w = this.runtime.worker();
        if (w == null) this.line(sender, note("Diagnostic writer disabled."));
        else {
            this.line(sender, pair("Report workers", w.active()).append(separator()).append(pair("Queue", w.queued() + "/" + w.capacity())));
            this.line(sender, pair("Completed", w.completed()).append(separator()).append(pair("Rejected", w.rejected())).append(separator()).append(pair("Failed", w.failures())));
        }
        var chunks = this.runtime.chunkSerializer();
        if (chunks == null) this.line(sender, note("Chunk serializer disabled."));
        else {
            this.line(sender, pair("Chunk workers", chunks.active() + "/" + chunks.workers()).append(separator())
                .append(pair("Queue", chunks.queued() + "/" + chunks.capacity())));
            this.line(sender, pair("Oldest / last queue / copy / compute ms", String.format(Locale.ROOT, "%.2f / %.2f / %.2f / %.2f",
                chunks.oldestQueueMs(), chunks.lastQueueMs(), chunks.lastSnapshotMs(), chunks.lastComputeMs())));
            this.line(sender, pair("Completed / fallback / failed / cancelled", chunks.completed() + " / " + chunks.fallbacks() + " / " + chunks.failures() + " / " + chunks.cancelled()));
        }
        this.line(sender, note("Quantum budget covers diagnostics and chunk serialization; gameplay stays on the main thread."));
    }

    private void profile(CommandSender sender) {
        this.line(sender, pair("Profile", this.runtime.config().profile()).append(separator()).append(pair("Safe mode", this.runtime.config().safeMode())));
        this.line(sender, pair("Performance overrides", this.runtime.config().performanceActive() ? "enabled" : "disabled"));
        this.line(sender, pair("Login admission interval", this.runtime.config().loginAdmission().intervalSeconds() + " seconds"));
        this.line(sender, note("Edit quantumspigot.yml; /quantum reload validates and swaps the immutable snapshot. World and worker changes require restart."));
    }

    private void configuration(CommandSender sender) {
        this.profile(sender);
        var g = GlobalConfiguration.get();
        this.line(sender, pair("Chunk rates / player / second", "generate=" + g.chunkLoadingBasic.playerMaxChunkGenerateRate
            + ", load=" + g.chunkLoadingBasic.playerMaxChunkLoadRate + ", send=" + g.chunkLoadingBasic.playerMaxChunkSendRate));
        this.line(sender, pair("Concurrent chunks / player", "generate=" + g.chunkLoadingAdvanced.playerMaxConcurrentChunkGenerates
            + ", load=" + g.chunkLoadingAdvanced.playerMaxConcurrentChunkLoads));
        this.line(sender, pair("Bundled Spark", g.spark.enabled));
        this.line(sender, pair("Experimental algorithms", this.runtime.config().algorithms()));
        this.line(sender, pair("Experimental mechanics", this.runtime.config().mechanics()));
        this.line(sender, pair("Experimental worldgen", this.runtime.config().worldgen()));
        this.line(sender, pair("Experimental async", this.runtime.config().async()));
        this.line(sender, pair("Experimental network", this.runtime.config().network()));
        var worlds = sender instanceof Player player ? List.of(player.getWorld()) : this.server.getWorlds();
        for (var world : worlds) {
            var level = ((CraftWorld) world).getHandle();
            var p = level.paperConfig();
            this.line(sender, Component.text(world.getKey().asString(), NamedTextColor.AQUA));
            this.line(sender, pair("Hopper transfer/check/amount", level.spigotConfig.hopperTransfer + "/" + level.spigotConfig.hopperCheck + "/" + level.spigotConfig.hopperAmount));
            this.line(sender, pair("Hopper cooldown / skip event / ignore blocks", p.hopper.cooldownWhenFull + "/" + p.hopper.disableMoveEvent + "/" + p.hopper.ignoreOccludingBlocks));
            this.line(sender, pair("Redstone", p.misc.redstoneImplementation));
            this.line(sender, pair("Armor stand tick/collision/gravity", p.entities.armorStands.tick + "/" + p.entities.armorStands.doCollisionEntityLookups + "/" + level.quantumArmorStandGravity));
            this.line(sender, pair("Armor stand movement/water", level.purpurConfig.armorstandMovement + "/" + level.purpurConfig.armorstandWaterMovement));
            this.line(sender, pair("Anti-Xray enabled/mode/height/radius", p.anticheat.antiXray.enabled + "/" + p.anticheat.antiXray.engineMode.getId() + "/" + p.anticheat.antiXray.maxBlockHeight + "/" + p.anticheat.antiXray.updateRadius));
            this.line(sender, pair("Anti-Xray lava / permission", p.anticheat.antiXray.lavaObscures + "/" + p.anticheat.antiXray.usePermission));
            this.line(sender, pair("Block entities", p.unsupportedSettings.ticking.blockEntities).append(separator()).append(pair("Block/fluid tick budget", p.environment.maxBlockTicks + "/" + p.environment.maxFluidTicks)));
            this.line(sender, pair("Autosave chunks/tick", p.chunks.maxAutoSaveChunksPerTick));
            this.line(sender, pair("Experimental waypoint collections", level.getWaypointManager().quantumWaypointCollections));
        }
        this.line(sender, note("Effective values. Root source: quantumspigot.yml. World and worker settings are restart-required."));
    }

    private void gc(CommandSender sender) {
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        this.line(sender, pair("Heap used", memory(heap.getUsed())).append(separator()).append(pair("Maximum", memory(heap.getMax()))));
        this.line(sender, pair("Committed", memory(heap.getCommitted())));
        if (sender instanceof ConsoleCommandSender) sender.sendMessage("[Quantum] Heap: " + heap);
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (sender instanceof ConsoleCommandSender) sender.sendMessage("[Quantum] " + gc.getName() + ": collections=" + gc.getCollectionCount() + " cumulative time=" + gc.getCollectionTime() + " ms");
            else this.line(sender, pair(gc.getName(), (gc.getCollectionCount() < 0 ? "unavailable" : gc.getCollectionCount() + " collections"))
                .append(separator()).append(pair("Total time", gc.getCollectionTime() < 0 ? "unavailable" : gc.getCollectionTime() + " ms")));
        }
        this.line(sender, note("Cumulative counters; this command does not force GC. Use GC logs/JFR for pauses."));
    }

    private void histogram(CommandSender sender) {
        if (!this.runtime.config().diagnostics().enabled()) { this.line(sender, warning("Histogram unavailable: diagnostics disabled.")); return; }
        long[] buckets = this.runtime.histogram();
        this.line(sender, note("Retained history up to " + this.runtime.config().diagnostics().historySeconds() + "s / "
            + this.runtime.config().diagnostics().historySeconds() * 20 + " ticks; upper bounds exclusive."));
        int[] bounds = {0, 10, 20, 40, 50, 75, 100, 250, 1000, buckets.length};
        for (int group = 0; group < bounds.length - 1; group++) {
            long count = 0;
            for (int bucket = bounds[group]; bucket < bounds[group + 1]; bucket++) count += buckets[bucket];
            this.line(sender, pair((group == bounds.length - 2 ? "1000+" : bounds[group] + ".." + bounds[group + 1]) + " ms", count));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 2 && sender.hasPermission("quantum.command." + args[0].toLowerCase(Locale.ROOT))) {
            String option = args[0].equalsIgnoreCase("mspt") ? "histogram" : args[0].equalsIgnoreCase("threads") ? "dump" : "";
            return !option.isEmpty() && option.startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of(option) : List.of();
        }
        if (args.length != 1) return List.of();
        return SUBCOMMANDS.stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .filter(name -> sender.hasPermission("quantum.command." + name)).toList();
    }
}
