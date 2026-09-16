package dev.quantumspigot.server.commands;

import dev.quantumspigot.server.QuantumRuntime;
import dev.quantumspigot.server.QuantumBuildInfo;
import dev.quantumspigot.server.metrics.TickHistory;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public final class QuantumCommand extends Command {
    private static final List<String> SUBCOMMANDS = List.of("version", "tps", "mspt", "health", "threads", "chunks", "entities", "plugins", "profile", "gc");
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
            server.getPluginManager().addPermission(new Permission("quantum.admin", "All Quantum diagnostics", PermissionDefault.OP, children));
        }
        server.getCommandMap().register("quantumspigot", new QuantumCommand(server, runtime));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        String subcommand = args.length == 0 ? "health" : args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(subcommand)) {
            sender.sendMessage(this.getUsage());
            return true;
        }
        if (!sender.hasPermission("quantum.command." + subcommand)) {
            sender.sendMessage("You do not have permission: quantum.command." + subcommand);
            return true;
        }
        // Commands issued through normal Bukkit dispatch must retain main-thread ownership.
        if (Thread.currentThread() != this.runtime.mainThread()) {
            sender.sendMessage("Quantum diagnostics must be requested through the main server thread.");
            return true;
        }
        switch (subcommand) {
            case "version" -> {
                sender.sendMessage(this.server.getVersion());
                sender.sendMessage("Upstream: " + QuantumBuildInfo.upstream() + " | Java " + Runtime.version());
                sender.sendMessage("Profile: " + this.runtime.config().profile() + " | Safe mode: " + this.runtime.config().safeMode());
            }
            case "tps" -> {
                this.tps(sender);
                this.mspt(sender, 60);
            }
            case "mspt" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("histogram")) this.histogram(sender);
                else for (int seconds : new int[] {60, 300, 900}) this.mspt(sender, seconds);
            }
            case "health" -> {
                TickHistory.Snapshot sample = this.runtime.ticks(60);
                String status = !this.runtime.config().diagnostics().enabled() ? "DIAGNOSTICS DISABLED"
                    : sample.samples() == 0 ? "WARMING UP"
                    : sample.p95() >= 75 ? "CRITICAL" : sample.p95() >= 50 ? "OVERLOADED" : sample.p95() >= 40 ? "DEGRADED" : "HEALTHY";
                sender.sendMessage("QuantumSpigot Health: " + status + " (based on observed tick p95)");
                this.tps(sender);
                this.mspt(sender, 60);
                sender.sendMessage(this.runtime.counts().toString());
                this.entities(sender);
                sender.sendMessage("Slow plugin tasks since startup: " + this.runtime.pluginStalls() + " | Lag incidents: " + this.runtime.lagIncidents());
                sender.sendMessage("Adaptive control: not installed | Async simulation: not installed");
                this.threads(sender);
            }
            case "threads" -> {
                this.threads(sender);
                if (args.length > 1 && args[1].equalsIgnoreCase("dump")) {
                    Thread.getAllStackTraces().entrySet().stream().sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Thread::getName))).limit(64).forEach(entry -> {
                            sender.sendMessage(entry.getKey().getName() + " " + entry.getKey().getState());
                            for (int i = 0; i < Math.min(12, entry.getValue().length); i++) sender.sendMessage("  " + entry.getValue()[i]);
                        });
                    sender.sendMessage("Snapshot limited to 64 threads / 12 frames each.");
                }
            }
            case "chunks" -> {
                sender.sendMessage("Loaded chunks: " + this.runtime.counts().loadedChunks());
                sender.sendMessage("Chunk packets sent since startup: " + this.measured(this.runtime.chunksSent()));
                sender.sendMessage("Player chunk queue entries last tick: " + (this.runtime.config().diagnostics().enabled()
                    ? this.runtime.chunkQueues() : "unavailable (diagnostics disabled)"));
                sender.sendMessage("Entries are summed per player, not unique chunks. Global IO/save queues: unavailable.");
            }
            case "entities" -> {
                sender.sendMessage("Loaded entities: " + this.runtime.counts().loadedEntities());
                this.entities(sender);
                sender.sendMessage("Invocation counts, not unique entities. Category timing costs: unavailable in Phase 1.");
            }
            case "plugins" -> {
                sender.sendMessage("Recent slow synchronous scheduled tasks (up to 64 incidents; no compatibility guarantee):");
                this.runtime.stalls().stream().skip(Math.max(0, this.runtime.stalls().size() - 10)).forEach(stall ->
                    sender.sendMessage(String.format(Locale.ROOT, "%s #%d %.2f ms (%s)", stall.plugin(), stall.taskId(), stall.durationMs(), stall.taskClass())));
            }
            case "profile" -> {
                sender.sendMessage("Active: " + this.runtime.config().profile() + "; safe mode=" + this.runtime.config().safeMode());
                sender.sendMessage("Phase 1 compatibility/balanced/custom: no simulation overrides. Diagnostics use exact YAML values.");
                sender.sendMessage("Safe-mode override: profile=compatibility. Performance/extreme and runtime switching are not implemented.");
            }
            case "gc" -> {
                sender.sendMessage("Heap: " + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());
                for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                    sender.sendMessage(gc.getName() + ": collections=" + gc.getCollectionCount() + " cumulative time=" + gc.getCollectionTime() + " ms");
                }
                sender.sendMessage("Cumulative JVM counters; recent individual pause durations are unavailable. Use Spark/JFR.");
            }
            default -> throw new IllegalStateException(subcommand);
        }
        return true;
    }

    private void tps(CommandSender sender) {
        double[] tps = this.server.getTPS();
        sender.sendMessage(String.format(Locale.ROOT, "TPS (upstream) 5s %.2f | 1m %.2f | 5m %.2f | 15m %.2f", tps[0], tps[1], tps[2], tps[3]));
    }

    private String measured(long value) {
        return this.runtime.config().diagnostics().enabled() ? Long.toString(value) : "unavailable (diagnostics disabled)";
    }

    private void entities(CommandSender sender) {
        sender.sendMessage("Entity tick invocations last tick: active=" + this.measured(this.runtime.lastEntityTicks())
            + " inactive=" + this.measured(this.runtime.lastInactiveEntityTicks()));
    }

    private void mspt(CommandSender sender, int seconds) {
        TickHistory.Snapshot sample = this.runtime.ticks(seconds);
        if (sample.samples() == 0) {
            sender.sendMessage("MSPT " + seconds + "s: no samples" + (this.runtime.config().diagnostics().enabled() ? " yet" : " (diagnostics disabled)"));
            return;
        }
        sender.sendMessage(String.format(Locale.ROOT,
            "MSPT %ds: avg %.2f | p50 %.2f | p75 %.2f | p95 %.2f | p99 %.2f | max %.2f | last %.2f (%d samples, %.1fs coverage)",
            seconds, sample.average(), sample.p50(), sample.p75(), sample.p95(), sample.p99(), sample.max(), sample.last(), sample.samples(), sample.coverageSeconds()));
    }

    private void threads(CommandSender sender) {
        sender.sendMessage("Main: " + this.runtime.mainThread().getName() + " " + this.runtime.mainThread().getState());
        sender.sendMessage("Quantum worker budget: " + this.runtime.budget());
        sender.sendMessage("Diagnostics: " + (this.runtime.worker() == null ? "disabled" : this.runtime.worker()));
        sender.sendMessage("Quantum compute pools: not installed. Upstream worker pools retain Paper/Purpur configuration.");
    }

    private void histogram(CommandSender sender) {
        if (!this.runtime.config().diagnostics().enabled()) {
            sender.sendMessage("MSPT histogram: unavailable (diagnostics disabled)");
            return;
        }
        long[] buckets = this.runtime.histogram();
        sender.sendMessage("MSPT histogram: retained samples, up to " + this.runtime.config().diagnostics().historySeconds()
            + "s / " + this.runtime.config().diagnostics().historySeconds() * 20 + " ticks; upper bounds exclusive.");
        int[] bounds = {0, 10, 20, 40, 50, 75, 100, 250, 1000, buckets.length};
        for (int group = 0; group < bounds.length - 1; group++) {
            long count = 0;
            for (int bucket = bounds[group]; bucket < bounds[group + 1]; bucket++) count += buckets[bucket];
            String range = group == bounds.length - 2 ? "1000+" : bounds[group] + ".." + bounds[group + 1];
            sender.sendMessage(range + " ms: " + count);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("mspt") && sender.hasPermission("quantum.command.mspt")) {
            return "histogram".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("histogram") : List.of();
        }
        if (args.length != 1) return List.of();
        return SUBCOMMANDS.stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .filter(name -> sender.hasPermission("quantum.command." + name)).toList();
    }
}
