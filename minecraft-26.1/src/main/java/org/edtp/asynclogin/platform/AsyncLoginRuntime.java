package org.edtp.asynclogin.platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.edtp.asynclogin.core.AsyncIoDispatcher;
import org.edtp.asynclogin.core.SingleThreadIoDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AsyncLoginRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("AsyncLogin");
    private static final AsyncIoDispatcher IO = new SingleThreadIoDispatcher("AsyncLogin IO Thread");
    private static final AtomicBoolean STOPPED = new AtomicBoolean();

    private AsyncLoginRuntime() {
    }

    public static void initialize() {
        LOGGER.info("AsyncLogin 26.1 adapter initialized");
    }

    public static AsyncIoDispatcher io() {
        return IO;
    }

    public static void observe(String description, CompletableFuture<?> future) {
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.error("Asynchronous IO transaction '{}' failed", description, unwrap(failure));
            }
        });
    }

    public static void shutdown() {
        if (!STOPPED.compareAndSet(false, true)) {
            return;
        }

        AsyncIoDispatcher.IoQueueSnapshot snapshot = IO.snapshot();
        LOGGER.info("Draining AsyncLogin IO queue ({} queued transaction(s))", snapshot.queuedTasks());
        IO.shutdownAndJoin();
        LOGGER.info("AsyncLogin IO queue drained after {} transaction(s)", IO.snapshot().completedTasks());
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
