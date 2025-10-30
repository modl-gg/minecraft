package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.impl.http.ModlHttpClientImpl;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class HttpManager {
    @NotNull
    private final ModlHttpClient httpClient;
    @NotNull
    private final String apiKey;
    @NotNull
    private final String apiUrl;
    @NotNull
    private final String panelUrl;
    private final boolean debugHttp;

    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp) {
        this.apiKey = key;
        // Normalize URL: remove trailing slash and /api if present
        String normalizedUrl = url.replaceAll("/+$", "");
        if (normalizedUrl.endsWith("/api")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 4);
        }
        this.apiUrl = normalizedUrl + "/api";
        this.panelUrl = normalizedUrl;
        this.debugHttp = debugHttp;
        this.httpClient = new ModlHttpClientImpl(apiUrl, key, debugHttp);
    }

    // Backward compatibility constructor with default debug=false
    public HttpManager(@NotNull String key, @NotNull String url) {
        this(key, url, false);
    }
}