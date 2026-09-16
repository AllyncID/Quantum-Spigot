package dev.quantumspigot.server.diagnostics;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsTest {
    @Test
    void saturationRejectsWithoutRunningOnCallerAndShutdownDrains() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();
        DiagnosticExecutor executor = new DiagnosticExecutor(1, failure::set);
        try {
            assertTrue(executor.submit(() -> {
                entered.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Test worker timed out");
                }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertTrue(executor.submit(completed::incrementAndGet));
            assertFalse(executor.submit(completed::incrementAndGet));
            assertEquals(0, completed.get());
            assertEquals(1, executor.snapshot().rejected());
        } finally {
            release.countDown();
            executor.close();
        }
        assertEquals(1, completed.get());
        assertNull(failure.get());
        assertFalse(executor.submit(completed::incrementAndGet));
    }

    @Test
    void workerFailuresRemainVisible() {
        AtomicInteger errors = new AtomicInteger();
        DiagnosticExecutor executor = new DiagnosticExecutor(1, error -> errors.incrementAndGet());
        executor.submit(() -> { throw new IllegalStateException("test failure"); });
        executor.close();
        assertEquals(1, errors.get());
        assertEquals(1, executor.snapshot().failures());
    }

    @Test
    void lagConsecutiveThresholdCooldownAndRetention() {
        LagSpikeRecorder recorder = new LagSpikeRecorder(75, 3, 30);
        assertFalse(recorder.record(0, 100));
        assertFalse(recorder.record(1, 20));
        assertFalse(recorder.record(2, 100));
        assertFalse(recorder.record(3, 100));
        assertTrue(recorder.record(4, 100));
        assertFalse(recorder.record(5, 100));
        for (int i = 1; i <= 100; i++) assertTrue(recorder.record(4 + i * 30_000_000_000L, 100));
        assertEquals(101, recorder.total());
        assertEquals(64, recorder.recent().size());
    }
}
