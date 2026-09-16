package dev.quantumspigot.server.metrics;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickHistoryTest {
    @Test
    void quantilesUseNearestRankAndRealDurations() {
        TickHistory history = new TickHistory(60);
        for (int i = 1; i <= 100; i++) history.record(i * 50_000_000L, i * 1_000_000L);
        var snapshot = history.snapshot(5_000_000_000L, 60);
        assertEquals(100, snapshot.samples());
        assertEquals(50.5, snapshot.average());
        assertEquals(50, snapshot.p50());
        assertEquals(75, snapshot.p75());
        assertEquals(95, snapshot.p95());
        assertEquals(99, snapshot.p99());
        assertEquals(100, snapshot.max());
        assertEquals(100, snapshot.last());
    }

    @Test
    void wrapEvictionAndWallClockWindowsStayBounded() {
        TickHistory history = new TickHistory(1);
        for (int i = 0; i < 25; i++) history.record(i * 10_000_000L, i * 1_000_000L);
        assertEquals(20, history.snapshot(250_000_000L, 1).samples());
        assertEquals(20, Arrays.stream(history.histogram(250_000_000L)).sum());
        assertEquals(0, history.snapshot(2_000_000_000L, 1).samples());
        assertEquals(0, Arrays.stream(history.histogram(2_000_000_000L)).sum());
        history.record(2_000_000_001L, 2_000_000_000L);
        assertEquals(1, history.histogram(2_000_000_001L)[1001]);
        assertEquals(2000, history.snapshot(2_000_000_001L, 1).p99());
    }

    @Test
    void emptyAndNegativeDurationsAreNotFakePerformance() {
        TickHistory history = new TickHistory(60);
        assertEquals(0, history.snapshot(0, 60).samples());
        assertThrows(IllegalArgumentException.class, () -> history.record(0, -1));
    }
}
