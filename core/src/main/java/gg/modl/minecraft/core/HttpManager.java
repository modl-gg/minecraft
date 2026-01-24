package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.impl.http.ModlHttpClientImpl;
import gg.modl.minecraft.core.impl.http.ModlHttpClientV2Impl;
import gg.modl.minecraft.core.impl.http.ModlHttpClientWithFallback;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.logging.Logger;

@Getter
public class HttpManager {
    private static final Logger logger = Logger.getLogger(HttpManager.class.getName());

    /**
     * Production V2 API URL
     */
    public static final String V2_API_URL = "https://api.modl.gg";

    /**
     * Testing V2 API URL
     */
    public static final String TESTING_API_URL = "https://api.cobl.gg";

    @NotNull
    private final ModlHttpClient httpClient;
    @NotNull
    private final String apiKey;
    @NotNull
    private final String apiUrl;
    @NotNull
    private final String panelUrl;
    private final boolean debugHttp;
    @NotNull
    private final ApiVersion apiVersion;

    /**
     * Creates an HttpManager with V2-first fallback behavior.
     * Tries V2 API first (api.modl.gg), falls back to V1 ({panel-url}/api) on failure.
     *
     * @param key API key
     * @param url Panel URL (e.g., https://yourserver.modl.gg)
     * @param debugHttp Enable debug HTTP logging
     */
    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp) {
        this(key, url, debugHttp, false);
    }

    /**
     * Creates an HttpManager with V2-first fallback behavior and optional testing API.
     * Tries V2 API first, falls back to V1 on failure.
     *
     * @param key API key
     * @param url Panel URL (e.g., https://yourserver.modl.gg)
     * @param debugHttp Enable debug HTTP logging
     * @param useTestingApi If true, uses api.cobl.gg for V2 API instead of api.modl.gg
     */
    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp, boolean useTestingApi) {
        this.apiKey = key;
        this.debugHttp = debugHttp;
        // Default to V2 - actual version used depends on fallback behavior
        this.apiVersion = ApiVersion.V2;

        // Normalize URL: remove trailing slash and /api if present
        String normalizedUrl = url.replaceAll("/+$", "");
        if (normalizedUrl.endsWith("/api")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 4);
        }
        this.panelUrl = normalizedUrl;

        // Extract server domain from panel URL (e.g., "yourserver.modl.gg" from "https://yourserver.modl.gg")
        String serverDomain = extractDomain(normalizedUrl);

        // Create V2 client (primary)
        String v2BaseUrl = useTestingApi ? TESTING_API_URL : V2_API_URL;
        String v2ApiUrl = v2BaseUrl + ApiVersion.V2.getBasePath();
        ModlHttpClient v2Client = new ModlHttpClientV2Impl(v2ApiUrl, key, serverDomain, debugHttp);

        // Create V1 client (fallback)
        String v1ApiUrl = normalizedUrl + ApiVersion.V1.getBasePath();
        ModlHttpClient v1Client = new ModlHttpClientImpl(v1ApiUrl, key, debugHttp);

        // Wrap in fallback client - tries V2 first, falls back to V1 on error
        this.httpClient = new ModlHttpClientWithFallback(v2Client, v1Client);
        this.apiUrl = v2ApiUrl; // Primary URL is V2

        logger.info("Using V2-first API with fallback: V2=" + v2ApiUrl + (useTestingApi ? " (testing)" : "") + ", V1=" + v1ApiUrl + ", Domain=" + serverDomain);
    }

    /**
     * Creates an HttpManager with a specific API version (no fallback).
     *
     * @param key API key
     * @param url Panel URL
     * @param debugHttp Enable debug HTTP logging
     * @param version The API version to use
     * @param useTestingApi If true and version is V2, uses api.cobl.gg
     */
    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp,
                       @NotNull ApiVersion version, boolean useTestingApi) {
        this.apiKey = key;
        this.debugHttp = debugHttp;
        this.apiVersion = version;

        // Normalize URL
        String normalizedUrl = url.replaceAll("/+$", "");
        if (normalizedUrl.endsWith("/api")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 4);
        }
        this.panelUrl = normalizedUrl;

        // Extract server domain from panel URL
        String serverDomain = extractDomain(normalizedUrl);

        if (version == ApiVersion.V2) {
            String v2BaseUrl = useTestingApi ? TESTING_API_URL : V2_API_URL;
            this.apiUrl = v2BaseUrl + ApiVersion.V2.getBasePath();
            this.httpClient = new ModlHttpClientV2Impl(apiUrl, key, serverDomain, debugHttp);
            logger.info("Using V2 API at: " + apiUrl + (useTestingApi ? " (testing)" : "") + ", Domain=" + serverDomain);
        } else {
            this.apiUrl = normalizedUrl + ApiVersion.V1.getBasePath();
            this.httpClient = new ModlHttpClientImpl(apiUrl, key, debugHttp);
            logger.info("Using V1 API at: " + apiUrl);
        }
    }

    // Backward compatibility constructor with default debug=false
    public HttpManager(@NotNull String key, @NotNull String url) {
        this(key, url, false);
    }

    /**
     * Extracts the domain from a URL.
     * e.g., "https://yourserver.modl.gg" -> "yourserver.modl.gg"
     */
    private static String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            // If parsing fails, try to extract domain manually
            String result = url;
            if (result.startsWith("https://")) {
                result = result.substring(8);
            } else if (result.startsWith("http://")) {
                result = result.substring(7);
            }
            // Remove trailing path
            int slashIndex = result.indexOf('/');
            if (slashIndex > 0) {
                result = result.substring(0, slashIndex);
            }
            return result;
        }
    }
}
