package gg.modl.minecraft.api.http;

import lombok.Getter;

/**
 * Thrown when the panel returns a gateway error, indicating it's likely restarting or temporarily unavailable.
 */
@Getter
public class PanelUnavailableException extends RuntimeException {
    private final String endpoint;
    private final int statusCode;

    public PanelUnavailableException(String endpoint, int statusCode, String message) {
        super(message);
        this.endpoint = endpoint;
        this.statusCode = statusCode;
    }
}