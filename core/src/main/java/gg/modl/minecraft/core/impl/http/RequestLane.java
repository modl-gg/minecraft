package gg.modl.minecraft.core.impl.http;

import gg.modl.minecraft.core.util.BoundedLookupExecutor;
import gg.modl.minecraft.core.util.CircuitBreaker;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

final class RequestLane {
    private final CircuitBreaker breaker = new CircuitBreaker();
    private final ThreadPoolExecutor executor;

    RequestLane(String threadNamePrefix, int threads, int queueCapacity) {
        this.executor = BoundedLookupExecutor.newDaemonPool(threadNamePrefix, threads, threads, queueCapacity, true);
    }

    CircuitBreaker breaker() {
        return breaker;
    }

    Executor executor() {
        return executor;
    }

    void shutdown(long timeoutSeconds) {
        BoundedLookupExecutor.shutdown(executor, timeoutSeconds);
    }
}
