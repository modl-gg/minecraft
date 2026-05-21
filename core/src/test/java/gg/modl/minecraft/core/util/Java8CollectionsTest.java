package gg.modl.minecraft.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java8CollectionsTest {
    @Test
    void orTimeoutCancelsScheduledTimeoutWhenFutureCompletesEarly() {
        RecordingScheduledExecutorService scheduler = new RecordingScheduledExecutorService();
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> returned = Java8Collections.orTimeout(source, 1, TimeUnit.DAYS, scheduler);
        source.complete("done");

        assertSame(source, returned);
        assertTrue(scheduler.timeoutFuture.cancelled);
        assertFalse(scheduler.timeoutFuture.mayInterruptIfRunning);
    }

    @Test
    void orTimeoutCompletesIncompleteFutureExceptionallyWhenTimeoutRuns() {
        RecordingScheduledExecutorService scheduler = new RecordingScheduledExecutorService();
        CompletableFuture<String> source = new CompletableFuture<>();

        Java8Collections.orTimeout(source, 1, TimeUnit.DAYS, scheduler);
        scheduler.timeoutCommand.run();

        CompletionException thrown = assertThrows(CompletionException.class, source::join);
        assertInstanceOf(TimeoutException.class, thrown.getCause());
        assertTrue(source.isCompletedExceptionally());
    }

    private static final class RecordingScheduledExecutorService extends AbstractExecutorService
            implements ScheduledExecutorService {
        private Runnable timeoutCommand;
        private RecordingScheduledFuture timeoutFuture;

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            timeoutCommand = command;
            timeoutFuture = new RecordingScheduledFuture();
            return timeoutFuture;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static final class RecordingScheduledFuture implements ScheduledFuture<Object> {
        private boolean cancelled;
        private boolean mayInterruptIfRunning;

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed delayed) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            this.mayInterruptIfRunning = mayInterruptIfRunning;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
        }
    }
}
