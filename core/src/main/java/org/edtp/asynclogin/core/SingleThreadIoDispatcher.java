package org.edtp.asynclogin.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An unbounded FIFO whose only worker performs every submitted operation serially.
 * Submission never waits for queue capacity and never executes work on the caller.
 */
public final class SingleThreadIoDispatcher implements AsyncIoDispatcher {
    private static final IoTask<?> STOP = IoTask.stop();

    private final Object lifecycleLock = new Object();
    private final LinkedBlockingQueue<IoTask<?>> queue = new LinkedBlockingQueue<>();
    private final AtomicLong submittedTasks = new AtomicLong();
    private final AtomicLong completedTasks = new AtomicLong();
    private final Thread worker;
    private volatile boolean accepting = true;
    private volatile IoTask<?> runningTask;

    public SingleThreadIoDispatcher(String threadName) {
        Objects.requireNonNull(threadName, "threadName");
        this.worker = Thread.ofPlatform()
            .name(threadName)
            .daemon(true)
            .unstarted(this::runWorker);
        this.worker.start();
    }

    @Override
    public <T> CompletableFuture<T> submit(String description, CheckedSupplier<T> operation) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(operation, "operation");
        if (isIoThread()) {
            throw new IllegalStateException("The IO worker cannot enqueue nested work");
        }

        IoTask<T> task = new IoTask<>(description, operation, System.nanoTime());
        synchronized (this.lifecycleLock) {
            if (!this.accepting) {
                throw new RejectedExecutionException("Async IO dispatcher is shutting down");
            }

            this.submittedTasks.incrementAndGet();
            this.queue.add(task);
        }
        return task.future;
    }

    @Override
    public boolean isIoThread() {
        return Thread.currentThread() == this.worker;
    }

    @Override
    public IoQueueSnapshot snapshot() {
        IoTask<?> oldest = this.runningTask;
        if (oldest == null) {
            oldest = this.queue.peek();
        }

        Duration oldestAge = oldest == null || oldest == STOP
            ? Duration.ZERO
            : Duration.ofNanos(Math.max(0L, System.nanoTime() - oldest.enqueuedAtNanos));
        int queued = this.queue.size();
        if (this.queue.contains(STOP)) {
            queued--;
        }

        return new IoQueueSnapshot(
            this.accepting,
            Math.max(queued, 0),
            this.submittedTasks.get(),
            this.completedTasks.get(),
            oldestAge
        );
    }

    @Override
    public void shutdownAndJoin() {
        if (isIoThread()) {
            throw new IllegalStateException("The IO worker cannot join itself");
        }

        synchronized (this.lifecycleLock) {
            if (this.accepting) {
                this.accepting = false;
                this.queue.add(STOP);
            }
        }

        boolean interrupted = false;
        while (this.worker.isAlive()) {
            try {
                this.worker.join();
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void runWorker() {
        while (true) {
            IoTask<?> task;
            try {
                task = this.queue.take();
            } catch (InterruptedException ignored) {
                continue;
            }

            if (task == STOP) {
                return;
            }

            this.runningTask = task;
            try {
                task.execute();
            } finally {
                this.completedTasks.incrementAndGet();
                this.runningTask = null;
            }
        }
    }

    private static final class IoTask<T> {
        private final String description;
        private final CheckedSupplier<T> operation;
        private final long enqueuedAtNanos;
        private final CompletableFuture<T> future = new CompletableFuture<>();

        private IoTask(String description, CheckedSupplier<T> operation, long enqueuedAtNanos) {
            this.description = description;
            this.operation = operation;
            this.enqueuedAtNanos = enqueuedAtNanos;
        }

        private static IoTask<Void> stop() {
            return new IoTask<>("stop", () -> null, 0L);
        }

        private void execute() {
            try {
                this.future.complete(this.operation.get());
            } catch (Throwable failure) {
                this.future.completeExceptionally(failure);
            }
        }

        @Override
        public String toString() {
            return this.description;
        }
    }
}
