package gg.modl.minecraft.api.http;

import lombok.Getter;

@Getter
public class ApiClientException extends RuntimeException {
    private final int statusCode;

    public ApiClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
