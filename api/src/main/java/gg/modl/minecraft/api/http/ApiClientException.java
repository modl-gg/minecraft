package gg.modl.minecraft.api.http;

import lombok.Getter;

/**
 * Thrown for non-2xx client-level (4xx) outcomes (e.g. 404 not-found, 401/403/405/409/429).
 * These are routine results that must NOT count toward the HTTP circuit breaker, so the
 * client error funnels treat this type specially and skip recordFailure().
 */
@Getter
public class ApiClientException extends RuntimeException {
    private final int statusCode;

    public ApiClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
