package dev.quantumspigot.server.diagnostics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Only immutable diagnostic payloads may cross into this worker. */
public final class DiagnosticExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong queueNanos = new AtomicLong();
    private final Consumer<Exception> onFailure;
    private final int capacity;

    public DiagnosticExecutor(int capacity, Consumer<Exception> onFailure) {
        this.capacity = capacity;
        this.onFailure = onFailure;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(capacity), runnable -> {
                Thread thread = new Thread(runnable, "Quantum Diagnostics");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean submit(Runnable task) {
        long submitted = System.nanoTime();
        try {
            this.executor.execute(() -> {
                this.queueNanos.set(System.nanoTime() - submitted);
                try {
                    task.run();
                } catch (Exception e) {
                    this.failures.incrementAndGet();
                    this.onFailure.accept(e);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            this.rejected.incrementAndGet();
            return false;
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(this.executor.getActiveCount(), this.executor.getQueue().size(), this.capacity,
            this.executor.getCompletedTaskCount(), this.rejected.get(), this.failures.get(), this.queueNanos.get() / 1_000_000.0);
    }

    @Override
    public void close() {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                int abandoned = this.executor.shutdownNow().size();
                this.rejected.addAndGet(abandoned);
                this.onFailure.accept(new IllegalStateException("Diagnostic shutdown timed out; abandoned queued reports: " + abandoned));
            }
        } catch (InterruptedException e) {
            this.rejected.addAndGet(this.executor.shutdownNow().size());
            Thread.currentThread().interrupt();
            this.onFailure.accept(e);
        }
    }

    public record Snapshot(int active, int queued, int capacity, long completed, long rejected, long failures,
                           double lastQueueLatencyMs) {}
}
