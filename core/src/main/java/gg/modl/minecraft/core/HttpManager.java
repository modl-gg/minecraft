package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.impl.http.ModlHttpClientImpl;
import gg.modl.minecraft.core.impl.http.ModlHttpClientV2Impl;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    public static final String TESTING_API_URL = "https://api.modl.top";

    @NotNull
    private final ModlHttpClient httpClient;
    @NotNull
    private final HttpClientHolder httpClientHolder;
    @NotNull
    private final String apiKey;
    @NotNull
    private final String apiUrl;
    @NotNull
    private final String panelUrl;
    private final boolean debugHttp;
    @NotNull
    private final ApiVersion apiVersion;
    @NotNull
    private final String serverDomain;
    private final boolean useTestingApi;

    /**
     * Creates an HttpManager that determines API version at startup.
     * Checks V2 health first; if V2 is down, uses V1 for the entire session.
     * No per-request fallback - the version is determined once at startup.
     *
     * @param key API key
     * @param url Panel URL (e.g., https://yourserver.modl.gg)
     * @param debugHttp Enable debug HTTP logging
     */
    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp) {
        this(key, url, debugHttp, false);
    }

    /**
     * Creates an HttpManager that determines API version at startup.
     * Checks V2 health first; if V2 is down, uses V1 for the entire session.
     *
     * @param key API key
     * @param url Panel URL (e.g., https://yourserver.modl.gg)
     * @param debugHttp Enable debug HTTP logging
     * @param useTestingApi If true, uses api.modl.top for V2 API instead of api.modl.gg
     */
    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp, boolean useTestingApi) {
        this(key, url, debugHttp, useTestingApi, "auto");
    }

    /**
     * Creates an HttpManager with configurable API version selection.
     *
     * @param key API key
     * @param url Panel URL (e.g., https://yourserver.modl.gg)
     * @param debugHttp Enable debug HTTP logging
     * @param useTestingApi If true, uses api.modl.top for V2 API instead of api.modl.gg
     * @param forceVersion "auto" for health check, "v1" to force V1, "v2" to force V2
     */
    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp, boolean useTestingApi, @NotNull String forceVersion) {
        this.apiKey = key;
        this.debugHttp = debugHttp;
        this.useTestingApi = useTestingApi;

        // Normalize URL: remove trailing slash and /api if present
        String normalizedUrl = url.replaceAll("/+$", "");
        if (normalizedUrl.endsWith("/api")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 4);
        }
        this.panelUrl = normalizedUrl;

        // Extract server domain from panel URL
        this.serverDomain = extractDomain(normalizedUrl);

        // Determine which API to use at startup
        String v2BaseUrl = useTestingApi ? TESTING_API_URL : V2_API_URL;
        String v2ApiUrl = v2BaseUrl + ApiVersion.V2.getBasePath();
        String v1ApiUrl = normalizedUrl + ApiVersion.V1.getBasePath();

        // Determine API version based on forceVersion setting
        boolean useV2;
        if ("v1".equalsIgnoreCase(forceVersion)) {
            logger.info("Force-version set to V1 - skipping V2 health check");
            useV2 = false;
        } else if ("v2".equalsIgnoreCase(forceVersion)) {
            logger.info("Force-version set to V2 - skipping V2 health check");
            useV2 = true;
        } else {
            // Auto-detect based on health check
            logger.info("Checking V2 API availability at: " + v2BaseUrl + "/v1/health");
            useV2 = checkV2Health(v2BaseUrl, key, this.serverDomain);
        }

        if (useV2) {
            this.apiVersion = ApiVersion.V2;
            this.apiUrl = v2ApiUrl;
            this.httpClient = new ModlHttpClientV2Impl(apiUrl, key, this.serverDomain, debugHttp);
            this.httpClientHolder = new HttpClientHolder(this.httpClient, this.apiVersion);
            logger.info("==============================================");
            logger.info("MODL API: Using V2 API (centralized)");
            logger.info("  Base URL: " + apiUrl);
            logger.info("  Server Domain: " + this.serverDomain);
            logger.info("  Testing API: " + useTestingApi);
            logger.info("  Force Version: " + forceVersion);
            logger.info("==============================================");
        } else {
            this.apiVersion = ApiVersion.V1;
            this.apiUrl = v1ApiUrl;
            this.httpClient = new ModlHttpClientImpl(apiUrl, key, debugHttp);
            this.httpClientHolder = new HttpClientHolder(this.httpClient, this.apiVersion);
            logger.warning("==============================================");
            logger.warning("MODL API: Using V1 API (legacy)");
            logger.warning("  Base URL: " + apiUrl);
            logger.warning("  Force Version: " + forceVersion);
            logger.warning("  NOTE: V1 uses different endpoint paths!");
            logger.warning("  V1 endpoints: /api/minecraft/player/login, /api/minecraft/sync, etc.");
            logger.warning("  If your backend expects V2 paths, requests will fail with 405!");
            logger.warning("==============================================");
        }
    }

    /**
     * Creates an HttpManager with a specific API version (no health check).
     *
     * @param key API key
     * @param url Panel URL
     * @param debugHttp Enable debug HTTP logging
     * @param version The API version to use
     * @param useTestingApi If true and version is V2, uses api.modl.top
     */
    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp,
                       @NotNull ApiVersion version, boolean useTestingApi) {
        this.apiKey = key;
        this.debugHttp = debugHttp;
        this.apiVersion = version;
        this.useTestingApi = useTestingApi;

        // Normalize URL
        String normalizedUrl = url.replaceAll("/+$", "");
        if (normalizedUrl.endsWith("/api")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 4);
        }
        this.panelUrl = normalizedUrl;

        // Extract server domain from panel URL
        this.serverDomain = extractDomain(normalizedUrl);

        if (version == ApiVersion.V2) {
            String v2BaseUrl = useTestingApi ? TESTING_API_URL : V2_API_URL;
            this.apiUrl = v2BaseUrl + ApiVersion.V2.getBasePath();
            this.httpClient = new ModlHttpClientV2Impl(apiUrl, key, this.serverDomain, debugHttp);
            this.httpClientHolder = new HttpClientHolder(this.httpClient, this.apiVersion);
            logger.info("Using V2 API at: " + apiUrl + (useTestingApi ? " (testing)" : "") + ", Domain=" + this.serverDomain);
        } else {
            this.apiUrl = normalizedUrl + ApiVersion.V1.getBasePath();
            this.httpClient = new ModlHttpClientImpl(apiUrl, key, debugHttp);
            this.httpClientHolder = new HttpClientHolder(this.httpClient, this.apiVersion);
            logger.info("Using V1 API at: " + apiUrl);
        }
    }

    // Backward compatibility constructor with default debug=false
    public HttpManager(@NotNull String key, @NotNull String url) {
        this(key, url, false);
    }

    /**
     * Checks if V2 API is available by making a health check request.
     *
     * @param v2BaseUrl The V2 API base URL
     * @param apiKey The API key
     * @param serverDomain The server domain
     * @return true if V2 API is available, false otherwise
     */
    private boolean checkV2Health(String v2BaseUrl, String apiKey, String serverDomain) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            // Try the health endpoint
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(v2BaseUrl + "/v1/health"))
                    .header("X-API-Key", apiKey)
                    .header("X-Server-Domain", serverDomain)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("V2 API health check PASSED (status: " + response.statusCode() + ")");
                return true;
            } else {
                logger.warning("V2 API health check FAILED (status: " + response.statusCode() +")");
                return false;
            }
        } catch (Exception e) {
            logger.warning("V2 API health check FAILED with exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
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
