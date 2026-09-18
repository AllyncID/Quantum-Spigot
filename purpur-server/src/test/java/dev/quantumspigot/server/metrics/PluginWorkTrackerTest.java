package dev.quantumspigot.server.metrics;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static dev.quantumspigot.server.metrics.PluginWorkTracker.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class PluginWorkTrackerTest {
    @Test
    void nestedHooksSubtractChildrenAndKeepBoundedRecentSamples() {
        var time = new AtomicLong();
        var tracker = new PluginWorkTracker(1, time::get);
        int outer = tracker.begin(TASK, "A", "task");
        time.set(2_000_000);
        int child = tracker.begin(EVENT, "B", "event");
        time.set(5_000_000);
        tracker.end(child);
        time.set(10_000_000);
        tracker.end(outer);
        var parent = tracker.top(10).getFirst();
        assertEquals(10, parent.inclusiveMs());
        assertEquals(7, parent.exclusiveMs());
        assertEquals(10, tracker.top(10).stream().mapToDouble(PluginWorkTracker.Snapshot::exclusiveMs).sum());
        for (int i = 0; i < 100; i++) {
            int token = tracker.begin(TASK, "A", "task");
            time.addAndGet(1_000_000);
            tracker.end(token);
        }
        parent = tracker.top(1).getFirst();
        assertEquals(101, parent.samples());
        assertEquals(64, parent.recentSamples());
        assertEquals(1, parent.recentP95Ms()); // Old 10ms sample was evicted from the ring.
        assertEquals(10, parent.maxMs());
        assertThrows(IllegalStateException.class, () -> tracker.end(1));
    }

    @Test
    void samplingKeepsWholeNestedTreesAndBoundsGroupAndDepthMemory() {
        var time = new AtomicLong();
        var tracker = new PluginWorkTracker(16, () -> time.addAndGet(1_000_000));
        for (int i = 0; i < 1024; i++) {
            int root = tracker.begin(COMMAND, "A", "command");
            int child = tracker.begin(EVENT, "B", "event");
            assertEquals(root > 0, child > 0);
            tracker.end(child);
            tracker.end(root);
        }
        assertEquals(1024, tracker.roots());
        assertTrue(tracker.sampledRoots() > 0 && tracker.sampledRoots() < 1024);
        assertTrue(tracker.top(10).stream().allMatch(sample -> sample.samples() == tracker.sampledRoots()));

        var bounded = new PluginWorkTracker(1, time::get);
        for (int i = 0; i < 1100; i++) bounded.end(bounded.begin(EVENT, "Plugin" + i, "event"));
        assertEquals(1024, bounded.groupCount());
        assertEquals(76, bounded.droppedGroups());
        int[] nested = new int[140];
        for (int i = 0; i < nested.length; i++) nested[i] = bounded.begin(TASK, "Plugin0", "event");
        for (int i = nested.length - 1; i >= 0; i--) bounded.end(nested[i]);
        assertEquals(12, bounded.depthOverflows());
        assertEquals(1, bounded.begin(TASK, "Plugin0", "event"));
        bounded.end(1);
    }
}
