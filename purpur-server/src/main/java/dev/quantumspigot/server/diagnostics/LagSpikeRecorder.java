package dev.quantumspigot.server.diagnostics;

import java.util.ArrayDeque;
import java.util.List;

/** Main-thread state machine; persistent slow ticks produce rate-limited incidents. */
public final class LagSpikeRecorder {
    private final double threshold;
    private final int requiredTicks;
    private final long intervalNanos;
    private final ArrayDeque<Incident> incidents = new ArrayDeque<>();
    private int consecutive;
    private boolean recorded;
    private long lastRecorded;
    private long total;

    public LagSpikeRecorder(double thresholdMs, int requiredTicks, int intervalSeconds) {
        if (!Double.isFinite(thresholdMs) || thresholdMs <= 0 || requiredTicks < 1 || intervalSeconds < 1) {
            throw new IllegalArgumentException("invalid lag recorder thresholds");
        }
        this.threshold = thresholdMs;
        this.requiredTicks = requiredTicks;
        this.intervalNanos = intervalSeconds * 1_000_000_000L;
    }

    public boolean record(long now, double mspt) {
        if (mspt < this.threshold) {
            this.consecutive = 0;
            return false;
        }
        this.consecutive = Math.min(this.requiredTicks, this.consecutive + 1);
        if (this.consecutive < this.requiredTicks || (this.recorded && now - this.lastRecorded < this.intervalNanos)) return false;
        this.recorded = true;
        this.lastRecorded = now;
        this.total++;
        if (this.incidents.size() == 64) this.incidents.removeFirst();
        this.incidents.addLast(new Incident(now, mspt));
        return true;
    }

    public long total() { return this.total; }
    public List<Incident> recent() { return List.copyOf(this.incidents); }
    public record Incident(long monotonicNanos, double mspt) {}
}
