package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.impl.http.ModlHttpClientV2Impl;
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
    public static final String TESTING_API_URL = "https://api.modl.top";

    private static final String V2_BASE_PATH = "/v1";

    @NotNull
    private final ModlHttpClient httpClient;
    @NotNull
    private final HttpClientHolder httpClientHolder;
    @NotNull
    private final String apiKey;
    @NotNull
    private final String apiUrl;
    private final boolean debugHttp;
    @NotNull
    private final String serverDomain;
    private final boolean useTestingApi;
    @NotNull
    private final String panelUrl;

    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp, boolean useTestingApi) {
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

        String v2BaseUrl = useTestingApi ? TESTING_API_URL : V2_API_URL;
        this.apiUrl = v2BaseUrl + V2_BASE_PATH;
        this.httpClient = new ModlHttpClientV2Impl(apiUrl, key, this.serverDomain, debugHttp);
        this.httpClientHolder = new HttpClientHolder(this.httpClient);

        logger.info("==============================================");
        logger.info("MODL API: Using V2 API (centralized)");
        logger.info("  Base URL: " + apiUrl);
        logger.info("  Server Domain: " + this.serverDomain);
        logger.info("  Testing API: " + useTestingApi);
        logger.info("==============================================");
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
