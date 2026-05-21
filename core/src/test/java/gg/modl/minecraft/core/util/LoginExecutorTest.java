package gg.modl.minecraft.core.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginExecutorTest {
    @Test
    void runsLoginWorkOnNamedDaemonThread() throws Exception {
        try (LoginExecutor executor = new LoginExecutor("test-login", 1, 1)) {
            CompletableFuture<Thread> workerThread = new CompletableFuture<>();

            executor.runAsync(() -> workerThread.complete(Thread.currentThread()));

            Thread thread = workerThread.get(5, TimeUnit.SECONDS);
            assertTrue(thread.isDaemon());
            assertTrue(thread.getName().startsWith("test-login-"));
        }
    }

    @Test
    void rejectsNewWorkAfterShutdown() {
        LoginExecutor executor = new LoginExecutor("test-login", 1, 1);

        executor.shutdown();

        assertThrows(RejectedExecutionException.class, () -> executor.runAsync(() -> {}));
    }

    @Test
    void rejectsWorkBeyondConfiguredCapacity() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        LoginExecutor executor = new LoginExecutor("test-login", 1, 1);
        try {
            executor.runAsync(() -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(started.await(5, TimeUnit.SECONDS));
            executor.runAsync(() -> {});

            assertThrows(RejectedExecutionException.class, () -> executor.runAsync(() -> {}));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
