package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for the HTTP client that allows dynamic switching from V1 to V2.
 * All components should get the client through this holder rather than storing their own reference.
 */
public class HttpClientHolder {
    private final AtomicReference<ModlHttpClient> clientRef;
    private volatile ApiVersion apiVersion;

    public HttpClientHolder(@NotNull ModlHttpClient initialClient, @NotNull ApiVersion initialVersion) {
        this.clientRef = new AtomicReference<>(initialClient);
        this.apiVersion = initialVersion;
    }

    /**
     * Get the current HTTP client.
     */
    @NotNull
    public ModlHttpClient getClient() {
        return clientRef.get();
    }

    /**
     * Get the current API version.
     */
    @NotNull
    public ApiVersion getApiVersion() {
        return apiVersion;
    }

    /**
     * Update the HTTP client and API version (used when upgrading from V1 to V2).
     */
    public void update(@NotNull ModlHttpClient newClient, @NotNull ApiVersion newVersion) {
        this.clientRef.set(newClient);
        this.apiVersion = newVersion;
    }
}
