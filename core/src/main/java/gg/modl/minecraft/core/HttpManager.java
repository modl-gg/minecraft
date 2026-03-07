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

    public static final String V2_API_URL = "https://api.modl.gg";
    public static final String TESTING_API_URL = "https://api.modl.top";

    private static final String V2_BASE_PATH = "/v1";

    private @NotNull final ModlHttpClient httpClient;
    private @NotNull final HttpClientHolder httpClientHolder;
    private @NotNull final String apiKey, apiUrl, serverDomain, panelUrl;
    private final boolean debugHttp, useTestingApi, queryMojang;

    public HttpManager(@NotNull String key, @NotNull String url, boolean debugHttp, boolean useTestingApi, boolean queryMojang) {
        this.apiKey = key;
        this.debugHttp = debugHttp;
        this.useTestingApi = useTestingApi;
        this.queryMojang = queryMojang;

        String normalizedUrl = url.replaceAll("/+$", "");
        if (normalizedUrl.endsWith("/api")) normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 4);
        this.panelUrl = normalizedUrl;

        this.serverDomain = extractDomain(normalizedUrl);

        String v2BaseUrl = useTestingApi ? TESTING_API_URL : V2_API_URL;
        this.apiUrl = v2BaseUrl + V2_BASE_PATH;
        this.httpClient = new ModlHttpClientV2Impl(apiUrl, key, this.serverDomain, debugHttp);
        this.httpClientHolder = new HttpClientHolder(this.httpClient);

        if (debugHttp) {
            logger.info("Base URL: " + apiUrl);
            logger.info("Server Domain: " + this.serverDomain);
            logger.info("Testing API: " + useTestingApi);
        }
    }

    private static String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            String result = url;
            if (result.startsWith("https://")) result = result.substring(8);
            else if (result.startsWith("http://")) result = result.substring(7);
            int slashIndex = result.indexOf('/');
            if (slashIndex > 0) result = result.substring(0, slashIndex);
            return result;
        }
    }
}
