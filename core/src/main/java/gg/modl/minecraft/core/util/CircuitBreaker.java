package gg.modl.minecraft.core.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class CircuitBreaker {
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final long DEFAULT_RETRY_TIMEOUT_MS = 30_000;

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long timeoutMillis;
    private final long retryTimeoutMillis;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong nextRetryTime = new AtomicLong(0);

    public CircuitBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_TIMEOUT_MS, DEFAULT_RETRY_TIMEOUT_MS);
    }

    public CircuitBreaker(int failureThreshold, long timeoutMillis, long retryTimeoutMillis) {
        this.failureThreshold = failureThreshold;
        this.timeoutMillis = timeoutMillis;
        this.retryTimeoutMillis = retryTimeoutMillis;
    }

    public boolean allowRequest() {
        State currentState = state.get();
        long currentTime = System.currentTimeMillis();

        return switch (currentState) {
            case CLOSED -> true;
            case OPEN -> {
                if (currentTime >= nextRetryTime.get() && state.compareAndSet(State.OPEN, State.HALF_OPEN)) yield true;
                yield false;
            }
            case HALF_OPEN -> true;
        };
    }

    public void recordSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) reset();
        } else if (currentState == State.CLOSED) {
            int currentFailures = failureCount.get();
            if (currentFailures > 0) failureCount.set(Math.max(0, currentFailures - 1));
        }
    }

    public void recordFailure() {
        State currentState = state.get();
        long currentTime = System.currentTimeMillis();

        int failures = failureCount.incrementAndGet();

        if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) nextRetryTime.set(currentTime + retryTimeoutMillis);
        } else if (currentState == State.CLOSED && failures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) nextRetryTime.set(currentTime + timeoutMillis);
        }
    }

    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        nextRetryTime.set(0);
    }
}
