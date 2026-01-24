package gg.modl.minecraft.api.http;

import lombok.Getter;

/**
 * API version enumeration for MODL HTTP API.
 * V1: Legacy API at {panel-url}/v1
 * V2: New centralized API at api.modl.gg/v1 (or api.cobl.gg/v1 for testing)
 */
@Getter
public enum ApiVersion {
    /**
     * Legacy V1 API - accessed at {panel-url}/v1
     * Uses X-API-Key header for authentication
     */
    V1("/v1"),

    /**
     * New V2 API - accessed at api.modl.gg/v1 or api.cobl.gg/v1
     * Uses X-API-Key and X-Server-Domain headers for authentication
     */
    V2("/v1");

    private final String basePath;

    ApiVersion(String basePath) {
        this.basePath = basePath;
    }
}
