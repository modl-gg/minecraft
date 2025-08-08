package gg.modl.minecraft.core.util;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Simple circuit breaker implementation to handle panel unavailability gracefully.
 * Prevents cascading failures when the panel is down by temporarily blocking requests.
 */
public class CircuitBreaker {
    private static final Logger logger = Logger.getLogger(CircuitBreaker.class.getName());
    
    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, blocking requests
        HALF_OPEN  // Testing if service is back up
    }
    
    private final String name;
    private final int failureThreshold;
    private final long timeoutMillis;
    private final long retryTimeoutMillis;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong nextRetryTime = new AtomicLong(0);
    
    /**
     * Create a circuit breaker with default settings
     */
    public CircuitBreaker(String name) {
        this(name, 5, 60000, 30000); // 5 failures, 60s timeout, 30s retry
    }
    
    /**
     * Create a circuit breaker with custom settings
     * 
     * @param name Circuit breaker name for logging
     * @param failureThreshold Number of failures before opening circuit
     * @param timeoutMillis How long to keep circuit open (ms)
     * @param retryTimeoutMillis How long to wait before retrying in half-open state (ms)
     */
    public CircuitBreaker(String name, int failureThreshold, long timeoutMillis, long retryTimeoutMillis) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.timeoutMillis = timeoutMillis;
        this.retryTimeoutMillis = retryTimeoutMillis;
    }
    
    /**
     * Check if a request should be allowed through the circuit breaker
     */
    public boolean allowRequest() {
        State currentState = state.get();
        long currentTime = System.currentTimeMillis();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                if (currentTime >= nextRetryTime.get()) {
                    // Try to transition to half-open
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        logger.info(String.format("Circuit breaker [%s] transitioning to HALF_OPEN", name));
                        return true;
                    }
                }
                return false;
                
            case HALF_OPEN:
                // Only allow one request at a time in half-open state
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * Record a successful operation
     */
    public void recordSuccess() {
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            // Successful request in half-open state - close the circuit
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                reset();
                logger.info(String.format("Circuit breaker [%s] closed after successful request", name));
            }
        } else if (currentState == State.CLOSED) {
            // Reset failure count on success
            int currentFailures = failureCount.get();
            if (currentFailures > 0) {
                failureCount.set(Math.max(0, currentFailures - 1));
            }
            successCount.incrementAndGet();
        }
    }
    
    /**
     * Record a failed operation
     */
    public void recordFailure() {
        State currentState = state.get();
        long currentTime = System.currentTimeMillis();
        
        lastFailureTime.set(currentTime);
        int failures = failureCount.incrementAndGet();
        
        if (currentState == State.HALF_OPEN) {
            // Failed request in half-open state - open the circuit again
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                nextRetryTime.set(currentTime + retryTimeoutMillis);
                logger.warning(String.format("Circuit breaker [%s] opened again after failed retry", name));
            }
        } else if (currentState == State.CLOSED && failures >= failureThreshold) {
            // Too many failures - open the circuit
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                nextRetryTime.set(currentTime + timeoutMillis);
                logger.warning(String.format("Circuit breaker [%s] opened after %d failures", name, failures));
            }
        }
    }
    
    /**
     * Reset the circuit breaker to closed state
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(0);
        nextRetryTime.set(0);
    }
    
    /**
     * Get current state of the circuit breaker
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Get current failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Get current success count
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Check if the circuit breaker is currently blocking requests
     */
    public boolean isOpen() {
        return state.get() == State.OPEN;
    }
    
    /**
     * Get status information for monitoring
     */
    public String getStatus() {
        State currentState = state.get();
        long currentTime = System.currentTimeMillis();
        long timeSinceLastFailure = lastFailureTime.get() > 0 ? currentTime - lastFailureTime.get() : -1;
        long timeUntilRetry = nextRetryTime.get() > currentTime ? nextRetryTime.get() - currentTime : 0;
        
        return String.format("CircuitBreaker[%s] State=%s, Failures=%d, Successes=%d, TimeSinceLastFailure=%dms, TimeUntilRetry=%dms",
                name, currentState, failureCount.get(), successCount.get(), timeSinceLastFailure, timeUntilRetry);
    }
}
