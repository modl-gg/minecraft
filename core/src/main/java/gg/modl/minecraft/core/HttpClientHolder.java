package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ModlHttpClient;
import org.jetbrains.annotations.NotNull;

/**
 * Holder for the HTTP client. All components should get the client through this holder.
 */
public class HttpClientHolder {
    private final ModlHttpClient client;

    public HttpClientHolder(@NotNull ModlHttpClient client) {
        this.client = client;
    }

    @NotNull
    public ModlHttpClient getClient() {
        return client;
    }
}
