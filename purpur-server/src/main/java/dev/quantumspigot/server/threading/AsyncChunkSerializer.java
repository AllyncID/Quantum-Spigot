package dev.quantumspigot.server.threading;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.jspecify.annotations.Nullable;

/** Workers serialize private section copies. Construction, submission and packet events belong to the owner. */
public final class AsyncChunkSerializer implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Semaphore slots;
    private final int workers;
    private final int capacity;
    private final BiConsumer<Connection, Throwable> onFailure;
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong queueNanos = new AtomicLong();
    private final AtomicLong computeNanos = new AtomicLong();
    private final AtomicLong snapshotNanos = new AtomicLong();

    public AsyncChunkSerializer(int workers, int capacity, BiConsumer<Connection, Throwable> onFailure) {
        if (workers < 1 || capacity < 1) throw new IllegalArgumentException("Positive worker and queue sizes required");
        this.workers = workers;
        this.capacity = capacity;
        this.slots = new Semaphore(workers + capacity);
        this.onFailure = onFailure;
        AtomicLong ids = new AtomicLong();
        this.executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(capacity), task -> {
                Thread thread = new Thread(task, "Quantum Chunk Serializer " + ids.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Reserve before copying. A null result tells the caller to use the unchanged native path. */
    public @Nullable ClientboundLevelChunkWithLightPacket submit(Supplier<ClientboundLevelChunkWithLightPacket> copy, Connection connection) {
        if (this.executor.isShutdown() || !this.slots.tryAcquire()) {
            this.fallbacks.incrementAndGet();
            return null;
        }
        ClientboundLevelChunkWithLightPacket packet;
        long started = System.nanoTime();
        try {
            packet = copy.get(); // Immediate owner-thread call; supplier is never retained by a worker.
        } catch (RuntimeException | Error error) {
            this.slots.release();
            throw error;
        }
        this.snapshotNanos.set(System.nanoTime() - started);
        try {
            this.executor.execute(new Job(packet, connection));
        } catch (RejectedExecutionException fullOrStopped) {
            this.slots.release();
            this.fallbacks.incrementAndGet();
            packet.quantumFinishSnapshot(); // Still on owner; no queued unreadable packet or repeated world copy.
        }
        return packet;
    }

    private final class Job implements Runnable {
        private final ClientboundLevelChunkWithLightPacket packet;
        private final Connection connection;
        private final long submitted = System.nanoTime();

        private Job(ClientboundLevelChunkWithLightPacket packet, Connection connection) {
            this.packet = packet;
            this.connection = connection;
        }

        @Override
        public void run() {
            long started = System.nanoTime();
            queueNanos.set(started - this.submitted);
            try {
                if (this.connection.isConnected()) this.packet.quantumFinishSnapshot();
                else {
                    this.packet.quantumDiscardSnapshot();
                    cancelled.incrementAndGet();
                }
            } catch (Throwable error) {
                this.packet.quantumDiscardSnapshot();
                failures.incrementAndGet();
                // Handler schedules a transport disconnect on the owner, clearing the readiness barrier safely.
                onFailure.accept(this.connection, error);
            } finally {
                computeNanos.set(System.nanoTime() - started);
                slots.release();
            }
        }

        private void abandon() {
            this.packet.quantumDiscardSnapshot();
            cancelled.incrementAndGet();
            slots.release();
            if (this.connection.isConnected()) onFailure.accept(this.connection, new IllegalStateException("Chunk serializer stopped"));
        }
    }

    public Snapshot snapshot() {
        Runnable first = this.executor.getQueue().peek();
        double oldestMs = first instanceof Job job ? (System.nanoTime() - job.submitted) / 1_000_000.0 : 0;
        return new Snapshot(this.workers, this.executor.getActiveCount(), this.executor.getQueue().size(), this.capacity,
            this.executor.getCompletedTaskCount(), this.fallbacks.get(), this.failures.get(), this.cancelled.get(),
            oldestMs, this.queueNanos.get() / 1_000_000.0, this.snapshotNanos.get() / 1_000_000.0, this.computeNanos.get() / 1_000_000.0);
    }

    @Override
    public void close() {
        this.executor.shutdown();
        try {
            if (this.executor.awaitTermination(5, TimeUnit.SECONDS)) return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        for (Runnable task : this.executor.shutdownNow()) ((Job) task).abandon();
    }

    public record Snapshot(int workers, int active, int queued, int capacity, long completed, long fallbacks,
                           long failures, long cancelled, double oldestQueueMs, double lastQueueMs,
                           double lastSnapshotMs, double lastComputeMs) {}
}
