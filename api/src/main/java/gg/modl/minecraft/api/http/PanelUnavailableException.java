package gg.modl.minecraft.api.http;

import lombok.Getter;

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