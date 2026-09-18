package dev.quantumspigot.server.metrics;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Owner-thread sampled hook timings. Stores names and numbers, never plugin/event/world objects. */
public final class PluginWorkTracker {
    public enum Kind { TASK, EVENT, COMMAND }
    private static final int MAX_GROUPS = 1024, MAX_DEPTH = 128, RECENT_SAMPLES = 64;
    private final Map<Key, Totals> groups = new HashMap<>();
    private final long[] starts = new long[MAX_DEPTH], children = new long[MAX_DEPTH];
    private final Key[] keys = new Key[MAX_DEPTH];
    private final int sampleEvery;
    private final LongSupplier clock;
    private final java.util.SplittableRandom sampler = new java.util.SplittableRandom(0x514e544dL);
    private int depth;
    private boolean sampling;
    private long roots, sampledRoots, droppedGroups, depthOverflows;

    public PluginWorkTracker(int sampleEvery) { this(sampleEvery, System::nanoTime); }

    PluginWorkTracker(int sampleEvery, LongSupplier clock) {
        if (sampleEvery < 1 || sampleEvery > 1024) throw new IllegalArgumentException("sampleEvery must be 1..1024");
        this.sampleEvery = sampleEvery;
        this.clock = clock;
    }

    /** Zero belongs to disabled hooks; -1 balances an unsampled/depth-limited entry. */
    public int begin(Kind kind, String plugin, String operation) {
        if (this.depth++ == 0) {
            this.roots++;
            this.sampling = this.sampleEvery == 1 || this.sampler.nextInt(this.sampleEvery) == 0;
            if (this.sampling) this.sampledRoots++;
        }
        if (!this.sampling) return -1;
        if (this.depth > MAX_DEPTH) { this.depthOverflows++; return -1; }
        int slot = this.depth - 1;
        this.keys[slot] = new Key(kind, bounded(plugin), bounded(operation));
        this.children[slot] = 0;
        this.starts[slot] = this.clock.getAsLong();
        return this.depth;
    }

    public void end(int token) {
        if (token == 0) return;
        if (this.depth <= 0 || token > 0 && token != this.depth) throw new IllegalStateException("unbalanced plugin timing hook");
        if (token < 0) { this.depth--; return; }
        int slot = --this.depth;
        long elapsed = Math.max(0, this.clock.getAsLong() - this.starts[slot]);
        long exclusive = Math.max(0, elapsed - this.children[slot]);
        if (slot > 0) this.children[slot - 1] += elapsed;
        Key key = this.keys[slot];
        this.keys[slot] = null;
        Totals totals = this.groups.get(key);
        if (totals == null) {
            if (this.groups.size() == MAX_GROUPS) { this.droppedGroups++; return; }
            this.groups.put(key, totals = new Totals());
        }
        totals.count++;
        totals.inclusive += elapsed;
        totals.exclusive += exclusive;
        totals.maximum = Math.max(totals.maximum, elapsed);
        totals.recent[totals.next] = elapsed;
        totals.next = (totals.next + 1) % RECENT_SAMPLES;
    }

    private static String bounded(String value) { return value.length() <= 256 ? value : value.substring(0, 256); }

    public List<Snapshot> top(int limit) {
        if (limit < 0) throw new IllegalArgumentException("negative limit");
        return this.groups.entrySet().stream().map(entry -> entry.getValue().snapshot(entry.getKey()))
            .sorted(Comparator.comparingDouble(Snapshot::exclusiveMs).reversed()).limit(limit).toList();
    }

    public long roots() { return this.roots; }
    public long sampledRoots() { return this.sampledRoots; }
    public long droppedGroups() { return this.droppedGroups; }
    public long depthOverflows() { return this.depthOverflows; }
    public int groupCount() { return this.groups.size(); }

    public record Key(Kind kind, String plugin, String operation) {}
    public record Snapshot(Key key, long samples, double inclusiveMs, double exclusiveMs,
                           double maxMs, int recentSamples, double recentP95Ms) {}
    private static final class Totals {
        long count, inclusive, exclusive, maximum;
        int next;
        final long[] recent = new long[RECENT_SAMPLES];
        Snapshot snapshot(Key key) {
            int size = (int) Math.min(this.count, RECENT_SAMPLES);
            long[] sorted = Arrays.copyOf(this.recent, size);
            Arrays.sort(sorted);
            return new Snapshot(key, this.count, this.inclusive / 1e6, this.exclusive / 1e6,
                this.maximum / 1e6, size, sorted[(int) Math.ceil(size * .95) - 1] / 1e6);
        }
    }
}
