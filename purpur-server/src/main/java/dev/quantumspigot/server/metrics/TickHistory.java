package dev.quantumspigot.server.metrics;

import java.util.Arrays;

/** Main-thread owned, allocation-free recording with bounded storage. */
public final class TickHistory {
    private final long[] durations;
    private final long[] timestamps;
    private final long retentionNanos;
    private final long[] histogram = new long[1002];
    private int next;
    private int count;

    public TickHistory(int seconds) {
        if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("seconds must be 1..3600");
        this.durations = new long[seconds * 20];
        this.timestamps = new long[this.durations.length];
        this.retentionNanos = seconds * 1_000_000_000L;
    }

    public void record(long nowNanos, long durationNanos) {
        if (durationNanos < 0) throw new IllegalArgumentException("negative tick duration");
        this.evict(nowNanos);
        if (this.count == this.durations.length) {
            this.histogram[bucket(this.durations[this.next])]--;
        } else {
            this.count++;
        }
        this.durations[this.next] = durationNanos;
        this.timestamps[this.next] = nowNanos;
        this.histogram[bucket(durationNanos)]++;
        this.next = (this.next + 1) % this.durations.length;
    }

    private void evict(long nowNanos) {
        while (this.count > 0) {
            int oldest = (this.next - this.count + this.durations.length) % this.durations.length;
            if (nowNanos - this.timestamps[oldest] <= this.retentionNanos) break;
            this.histogram[bucket(this.durations[oldest])]--;
            this.count--;
        }
    }

    private static int bucket(long nanos) {
        return (int) Math.min(1001L, nanos / 1_000_000L);
    }

    /** 1 ms buckets [0,1), ... [1000,1001), with overflow at index 1001. */
    public long[] histogram(long nowNanos) {
        this.evict(nowNanos);
        return this.histogram.clone();
    }

    public Snapshot snapshot(long nowNanos, int windowSeconds) {
        if (windowSeconds < 1) throw new IllegalArgumentException("positive window required");
        this.evict(nowNanos);
        long[] samples = new long[this.count];
        int size = 0;
        double sum = 0;
        long oldest = nowNanos;
        long last = 0;
        for (int offset = 1; offset <= this.count; offset++) {
            int index = (this.next - offset + this.durations.length) % this.durations.length;
            if (nowNanos - this.timestamps[index] > windowSeconds * 1_000_000_000L) break;
            long duration = this.durations[index];
            if (size == 0) last = duration;
            samples[size++] = duration;
            sum += duration;
            oldest = this.timestamps[index];
        }
        if (size == 0) return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0);
        Arrays.sort(samples, 0, size);
        return new Snapshot(size, (nowNanos - oldest) / 1_000_000_000.0,
            sum / size / 1_000_000.0, percentile(samples, size, .50), percentile(samples, size, .75),
            percentile(samples, size, .95), percentile(samples, size, .99),
            samples[size - 1] / 1_000_000.0, last / 1_000_000.0);
    }

    private static double percentile(long[] samples, int size, double percentile) {
        return samples[(int) Math.ceil(size * percentile) - 1] / 1_000_000.0;
    }

    public record Snapshot(int samples, double coverageSeconds, double average, double p50, double p75,
                           double p95, double p99, double max, double last) {}
}
