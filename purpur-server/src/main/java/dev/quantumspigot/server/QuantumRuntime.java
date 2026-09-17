package dev.quantumspigot.server;

import dev.quantumspigot.server.commands.QuantumCommand;
import dev.quantumspigot.server.config.QuantumConfig;
import dev.quantumspigot.server.config.QuantumTuning;
import dev.quantumspigot.server.diagnostics.DiagnosticExecutor;
import dev.quantumspigot.server.diagnostics.LagSpikeRecorder;
import dev.quantumspigot.server.metrics.TickHistory;
import dev.quantumspigot.server.threading.WorkerBudget;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;

/** Internal hooks are main-thread-only; the report writer receives immutable strings. */
public final class QuantumRuntime {
    private static volatile QuantumRuntime instance;
    private final Server server;
    private final Thread mainThread;
    private final QuantumConfig config;
    private final WorkerBudget budget;
    private final TickHistory ticks;
    private final LagSpikeRecorder lag;
    private final DiagnosticExecutor reports;
    private final ArrayDeque<PluginStall> stalls = new ArrayDeque<>();
    private long pluginStalls;
    private long lastPluginWarning;
    private boolean warnedPlugin;
    private long entityTicks;
    private long lastEntityTicks;
    private long inactiveEntityTicks;
    private long lastInactiveEntityTicks;
    private long chunksSent;
    private final long[] chunkQueues = new long[7];
    private final long[] lastChunkQueues = new long[7];
    private final long startedNanos = System.nanoTime();

    private QuantumRuntime(Server server, QuantumConfig config) {
        this.server = server;
        this.mainThread = Thread.currentThread();
        this.config = config;
        this.ticks = new TickHistory(config.diagnostics().historySeconds());
        this.lag = new LagSpikeRecorder(config.diagnostics().lagThresholdMs(), config.diagnostics().consecutiveTicks(),
            config.diagnostics().warningIntervalSeconds());
        boolean needsReports = config.diagnostics().enabled() && config.diagnostics().saveReports();
        this.budget = WorkerBudget.plan(Runtime.getRuntime().availableProcessors(), config.workers(), needsReports);
        java.util.logging.Logger logger = server.getLogger();
        this.reports = needsReports ? new DiagnosticExecutor(config.diagnostics().reportQueueCapacity(),
            error -> logger.log(Level.WARNING, "[QuantumSpigot] Diagnostic writer failed", error)) : null;
    }

    public static void initialize(Server server, boolean safeMode) throws IOException, InvalidConfigurationException {
        QuantumRuntime runtime = new QuantumRuntime(server, QuantumConfig.load(Path.of("config", "quantum"), safeMode));
        instance = runtime;
        QuantumTuning.applyGlobal(runtime.config, io.papermc.paper.configuration.GlobalConfiguration.get());
        if (runtime.config.performanceActive() && runtime.config.performance().disableBundledSpark()) {
            ((org.bukkit.craftbukkit.CraftServer) server).spark.disable();
        }
        QuantumCommand.register(server, runtime);
        server.getLogger().info("[QuantumSpigot] Phase 1 | profile=" + runtime.config.profile().name().toLowerCase(Locale.ROOT)
            + " | safe-mode=" + safeMode + " | diagnostics=" + runtime.config.diagnostics().enabled()
            + " | Quantum worker budget=" + runtime.budget.total() + " (diagnostics=" + runtime.budget.diagnostics() + ")");
        server.getLogger().info("[QuantumSpigot] Performance overrides=" + runtime.config.performanceActive()
            + "; async compute and adaptive control are not installed.");
    }

    public static boolean configureWorld(String dimensionKey, io.papermc.paper.configuration.WorldConfiguration paper,
                                        org.spigotmc.SpigotWorldConfig spigot, org.purpurmc.purpur.PurpurWorldConfig purpur) {
        QuantumRuntime runtime = instance;
        return runtime == null || QuantumTuning.applyWorld(runtime.config.worldTuning(dimensionKey), paper, spigot, purpur);
    }

    public static void shutdown() {
        QuantumRuntime runtime = instance;
        instance = null;
        if (runtime != null && runtime.reports != null) runtime.reports.close();
    }

    public static void recordTick(long durationNanos) {
        QuantumRuntime runtime = instance;
        if (runtime == null || !runtime.config.diagnostics().enabled()) return;
        long now = System.nanoTime();
        runtime.ticks.record(now, durationNanos);
        runtime.lastEntityTicks = runtime.entityTicks;
        runtime.entityTicks = 0;
        runtime.lastInactiveEntityTicks = runtime.inactiveEntityTicks;
        runtime.inactiveEntityTicks = 0;
        System.arraycopy(runtime.chunkQueues, 0, runtime.lastChunkQueues, 0, runtime.chunkQueues.length);
        java.util.Arrays.fill(runtime.chunkQueues, 0);
        if (runtime.lag.record(now, durationNanos / 1_000_000.0)) {
            runtime.server.getLogger().warning(String.format(Locale.ROOT,
                "[QuantumSpigot] Consecutive slow ticks: last %.2f ms; incident %d", durationNanos / 1_000_000.0, runtime.lag.total()));
            if (runtime.reports != null) {
                // All Bukkit/world access and formatting happen here, before crossing the worker boundary.
                String report = runtime.lagReport(now);
                int retention = runtime.config.diagnostics().retainedReports();
                if (!runtime.reports.submit(() -> writeReport(report, retention))) {
                    runtime.server.getLogger().warning("[QuantumSpigot] Diagnostic queue full; lag report dropped (see /quantum threads).");
                }
            }
        }
    }

    public static void recordEntityTick(boolean active) {
        QuantumRuntime runtime = instance;
        if (runtime != null && runtime.config.diagnostics().enabled()) {
            if (active) runtime.entityTicks++;
            else runtime.inactiveEntityTicks++;
        }
    }

    public static void recordChunkSent() {
        QuantumRuntime runtime = instance;
        if (runtime != null && runtime.config.diagnostics().enabled()) runtime.chunksSent++;
    }

    /** Called once per player in the existing main-thread chunk-loader loop. Entries are not unique chunks. */
    public static void recordPlayerChunkQueues(int load, int loading, int generation, int generating, int send, int ticking) {
        QuantumRuntime runtime = instance;
        if (runtime == null || !runtime.config.diagnostics().enabled()) return;
        runtime.chunkQueues[0]++;
        runtime.chunkQueues[1] += load;
        runtime.chunkQueues[2] += loading;
        runtime.chunkQueues[3] += generation;
        runtime.chunkQueues[4] += generating;
        runtime.chunkQueues[5] += send;
        runtime.chunkQueues[6] += ticking;
    }

    public static void recordPluginTask(String plugin, int taskId, String taskClass, long durationNanos) {
        QuantumRuntime runtime = instance;
        if (runtime == null || !runtime.config.diagnostics().enabled()
            || durationNanos < runtime.config.diagnostics().pluginThresholdMs() * 1_000_000.0) return;
        long now = System.nanoTime();
        runtime.pluginStalls++;
        if (runtime.stalls.size() == 64) runtime.stalls.removeFirst();
        runtime.stalls.addLast(new PluginStall(now, plugin, taskId, taskClass, durationNanos / 1_000_000.0));
        if (!runtime.warnedPlugin || now - runtime.lastPluginWarning >= runtime.config.diagnostics().warningIntervalSeconds() * 1_000_000_000L) {
            runtime.warnedPlugin = true;
            runtime.lastPluginWarning = now;
            runtime.server.getLogger().warning(String.format(Locale.ROOT,
                "[QuantumSpigot] Plugin %s sync task #%d (%s) took %.2f ms. Repeated warnings suppressed for %d seconds.",
                plugin, taskId, taskClass, durationNanos / 1_000_000.0, runtime.config.diagnostics().warningIntervalSeconds()));
        }
    }

    public QuantumConfig config() { return this.config; }
    public WorkerBudget budget() { return this.budget; }
    public Thread mainThread() { return this.mainThread; }
    public TickHistory.Snapshot ticks(int seconds) { return this.ticks.snapshot(System.nanoTime(), seconds); }
    public long[] histogram() { return this.ticks.histogram(System.nanoTime()); }
    public DiagnosticExecutor.Snapshot worker() { return this.reports == null ? null : this.reports.snapshot(); }
    public List<PluginStall> stalls() { return List.copyOf(this.stalls); }
    public long pluginStalls() { return this.pluginStalls; }
    public long lagIncidents() { return this.lag.total(); }
    public long lastEntityTicks() { return this.lastEntityTicks; }
    public long lastInactiveEntityTicks() { return this.lastInactiveEntityTicks; }
    public long chunksSent() { return this.chunksSent; }
    public ChunkQueues chunkQueues() {
        long[] q = this.lastChunkQueues;
        return new ChunkQueues(q[0], q[1], q[2], q[3], q[4], q[5], q[6]);
    }
    public double uptimeSeconds() { return (System.nanoTime() - this.startedNanos) / 1_000_000_000.0; }

    public Counts counts() {
        int chunks = 0;
        int entities = 0;
        for (World world : this.server.getWorlds()) {
            chunks += world.getChunkCount();
            entities += world.getEntityCount();
        }
        return new Counts(this.server.getOnlinePlayers().size(), chunks, entities);
    }

    private String lagReport(long now) {
        StringBuilder text = new StringBuilder("QuantumSpigot lag incident\n");
        text.append("timestamp=").append(Instant.now()).append('\n');
        text.append("version=").append(this.server.getVersion()).append('\n');
        text.append("upstream=").append(QuantumBuildInfo.upstream()).append('\n');
        text.append("profile=").append(this.config.profile()).append(" safeMode=").append(this.config.safeMode()).append('\n');
        text.append("window60s=").append(this.ticks.snapshot(now, 60)).append('\n');
        text.append("counts=").append(this.counts()).append(" activeEntityTicksLastTick=").append(this.lastEntityTicks)
            .append(" inactiveEntityTicksLastTick=").append(this.lastInactiveEntityTicks).append('\n');
        text.append("chunkPacketsSentSinceStart=").append(this.chunksSent).append('\n');
        text.append("playerChunkQueueEntriesLastTick=").append(this.chunkQueues()).append('\n');
        text.append("globalChunkIOAndSaveQueues=unavailable\n");
        text.append("worker=").append(this.worker()).append('\n');
        text.append("heap=").append(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()).append('\n');
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            text.append("gc=").append(gc.getName()).append(" count=").append(gc.getCollectionCount())
                .append(" cumulativeMs=").append(gc.getCollectionTime()).append('\n');
        }
        for (PluginStall stall : this.stalls) {
            if (now - stall.monotonicNanos() <= 300_000_000_000L) text.append("recentSlowTask=").append(stall).append('\n');
        }
        text.append("mainStack=post-tick capture, not a sampled stall cause\n");
        StackTraceElement[] stack = this.mainThread.getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 24); i++) text.append("  ").append(stack[i]).append('\n');
        return text.toString();
    }

    private static void writeReport(String contents, int retainedReports) {
        Path directory = Path.of("quantum-reports", "lag-spikes");
        try {
            Files.createDirectories(directory);
            Path report = directory.resolve("quantum-lag-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".txt");
            Files.writeString(report, contents);
            try (var paths = Files.list(directory)) {
                List<Path> reports = paths.filter(path -> path.getFileName().toString()
                    .matches("quantum-lag-[0-9]+-[a-f0-9-]{36}\\.txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
                for (int i = 0; i < reports.size() - retainedReports; i++) Files.deleteIfExists(reports.get(i));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Counts(int players, int loadedChunks, int loadedEntities) {}
    public record ChunkQueues(long sampledPlayers, long waitingLoad, long loading, long waitingGeneration,
                              long generating, long waitingSend, long waitingTicking) {}
    public record PluginStall(long monotonicNanos, String plugin, int taskId, String taskClass, double durationMs) {}
}
