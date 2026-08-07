package org.edtp.asynclogin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SingleThreadIoDispatcherTest {
    @Test
    void executesInFifoOrderOnOneWorker() throws Exception {
        SingleThreadIoDispatcher dispatcher = new SingleThreadIoDispatcher("test-io");
        try {
            List<Integer> order = Collections.synchronizedList(new ArrayList<>());
            List<String> threads = Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int index = 0; index < 100; index++) {
                int value = index;
                futures.add(dispatcher.submit("task-" + value, () -> {
                    order.add(value);
                    threads.add(Thread.currentThread().getName());
                }));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            assertEquals(100, order.size());
            for (int index = 0; index < 100; index++) {
                assertEquals(index, order.get(index));
            }
            assertEquals(List.of("test-io"), threads.stream().distinct().toList());
        } finally {
            dispatcher.shutdownAndJoin();
        }
    }

    @Test
    void failureCompletesOnlyThatFutureExceptionally() throws Exception {
        SingleThreadIoDispatcher dispatcher = new SingleThreadIoDispatcher("test-io");
        try {
            CompletableFuture<Void> failed = dispatcher.submit("failure", () -> {
                throw new IllegalStateException("boom");
            });
            CompletableFuture<Integer> next = dispatcher.submit("next", () -> 42);

            assertThrows(Exception.class, () -> failed.get(5, TimeUnit.SECONDS));
            assertEquals(42, next.get(5, TimeUnit.SECONDS));
        } finally {
            dispatcher.shutdownAndJoin();
        }
    }

    @Test
    void shutdownDrainsAndRejectsNewWork() throws Exception {
        SingleThreadIoDispatcher dispatcher = new SingleThreadIoDispatcher("test-io");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> first = dispatcher.submit("blocked", () -> {
            entered.countDown();
            release.await();
        });
        CompletableFuture<Integer> second = dispatcher.submit("after", () -> 7);

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        Thread shutdown = Thread.ofPlatform().start(dispatcher::shutdownAndJoin);
        while (dispatcher.snapshot().accepting()) {
            Thread.onSpinWait();
        }
        assertTrue(shutdown.isAlive());
        assertThrows(RejectedExecutionException.class, () -> dispatcher.submit("late", () -> null));

        release.countDown();
        shutdown.join(5_000L);
        assertFalse(shutdown.isAlive());
        assertFalse(first.isCompletedExceptionally());
        assertEquals(7, second.get(5, TimeUnit.SECONDS));
        assertEquals(2, dispatcher.snapshot().completedTasks());
    }

    @Test
    void rejectsNestedSubmissionFromWorkerWithoutKillingIt() throws Exception {
        SingleThreadIoDispatcher dispatcher = new SingleThreadIoDispatcher("test-io");
        try {
            CompletableFuture<Void> nested = dispatcher.submit("outer", () -> {
                assertThrows(IllegalStateException.class, () -> dispatcher.submit("inner", () -> null));
            });

            nested.get(5, TimeUnit.SECONDS);
            assertEquals(9, dispatcher.submit("next", () -> 9).get(5, TimeUnit.SECONDS));
        } finally {
            dispatcher.shutdownAndJoin();
        }
    }
}
