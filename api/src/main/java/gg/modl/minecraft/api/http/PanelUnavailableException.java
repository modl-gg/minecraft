package gg.modl.minecraft.api.http;

import lombok.Getter;

/**
 * Thrown when the panel returns a gateway error, indicating it's likely restarting or temporarily unavailable.
 */
@Getter
public class PanelUnavailableException extends RuntimeException {
    private final int statusCode;
    private final String endpoint;

    public PanelUnavailableException(int statusCode, String endpoint, String message) {
        super(message);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }
}