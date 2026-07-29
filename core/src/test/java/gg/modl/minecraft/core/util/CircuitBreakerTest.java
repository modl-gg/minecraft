package gg.modl.minecraft.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    private CircuitBreaker halfOpenBreakerWithClaimedProbe() {
        CircuitBreaker breaker = new CircuitBreaker(1, 0, 0);
        breaker.recordFailure();
        assertTrue(breaker.allowRequest());
        return breaker;
    }

    @Test
    void halfOpenAllowsExactlyOneProbe() {
        CircuitBreaker breaker = halfOpenBreakerWithClaimedProbe();
        assertFalse(breaker.allowRequest());
    }

    @Test
    void releaseProbeAfterRejectedSubmitAllowsALaterProbe() {
        CircuitBreaker breaker = halfOpenBreakerWithClaimedProbe();
        assertFalse(breaker.allowRequest());
        breaker.releaseProbe();
        assertTrue(breaker.allowRequest());
    }

    @Test
    void releaseProbeReopensWithFreshBackoff() {
        CircuitBreaker breaker = new CircuitBreaker(1, 0, 60_000);
        breaker.recordFailure();
        assertTrue(breaker.allowRequest());
        breaker.releaseProbe();
        assertFalse(breaker.allowRequest());
    }

    @Test
    void releaseProbeOnClosedBreakerIsHarmless() {
        CircuitBreaker breaker = new CircuitBreaker(1, 0, 0);
        breaker.releaseProbe();
        assertTrue(breaker.allowRequest());
        assertTrue(breaker.allowRequest());
    }

    @Test
    void successfulProbeClosesBreaker() {
        CircuitBreaker breaker = halfOpenBreakerWithClaimedProbe();
        breaker.recordSuccess();
        assertTrue(breaker.allowRequest());
        assertTrue(breaker.allowRequest());
    }

    @Test
    void failedProbeReopensAndReleasesToken() {
        CircuitBreaker breaker = halfOpenBreakerWithClaimedProbe();
        breaker.recordFailure();
        assertTrue(breaker.allowRequest());
        assertFalse(breaker.allowRequest());
    }
}
