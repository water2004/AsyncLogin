package org.edtp.asynclogin.core;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface AsyncIoDispatcher extends AutoCloseable {
    <T> CompletableFuture<T> submit(String description, CheckedSupplier<T> operation);

    default CompletableFuture<Void> submit(String description, CheckedRunnable operation) {
        return submit(description, () -> {
            operation.run();
            return null;
        });
    }

    default CompletableFuture<Void> barrier() {
        return submit("barrier", () -> null);
    }

    boolean isIoThread();

    IoQueueSnapshot snapshot();

    void shutdownAndJoin();

    @Override
    default void close() {
        shutdownAndJoin();
    }

    @FunctionalInterface
    interface CheckedRunnable {
        void run() throws Exception;
    }

    record IoQueueSnapshot(
        boolean accepting,
        int queuedTasks,
        long submittedTasks,
        long completedTasks,
        Duration oldestTaskAge
    ) {
    }
}
