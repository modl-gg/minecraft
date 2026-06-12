package gg.modl.minecraft.core;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.impl.http.ModlHttpClientV2Impl;
import gg.modl.minecraft.core.impl.http.ModlHttpClientV3Impl;
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
    private static final String V3_BASE_PATH = "/v3";

    /**
     * Kill-switch for the proto V3 HTTP client. Defaults to {@code true} (V3 proto); set
     * {@code -Dmodl.http.protoV3.enabled=false} or env {@code MODL_HTTP_PROTO_V3_ENABLED=false}
     * to fall back to the legacy V2 JSON client without a redeploy.
     */
    private static final String PROTO_V3_PROPERTY = "modl.http.protoV3.enabled";
    private static final String PROTO_V3_ENV = "MODL_HTTP_PROTO_V3_ENABLED";

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
        this.panelUrl = adjustPanelUrlForEnv(normalizedUrl, useTestingApi);

        this.serverDomain = extractDomain(normalizedUrl);

        String apiHost = useTestingApi ? TESTING_API_URL : V2_API_URL;
        boolean protoV3Enabled = isProtoV3Enabled();
        if (protoV3Enabled) {
            this.apiUrl = apiHost + V3_BASE_PATH;
            this.httpClient = new ModlHttpClientV3Impl(apiUrl, key, this.serverDomain, debugHttp);
        } else {
            this.apiUrl = apiHost + V2_BASE_PATH;
            this.httpClient = new ModlHttpClientV2Impl(apiUrl, key, this.serverDomain, debugHttp);
        }
        this.httpClientHolder = new HttpClientHolder(this.httpClient);

        if (debugHttp) {
            logger.info("Base URL: " + apiUrl);
            logger.info("Server Domain: " + this.serverDomain);
            logger.info("Testing API: " + useTestingApi);
            logger.info("Proto V3 client: " + protoV3Enabled);
        }
    }

    public void shutdown() {
        httpClient.shutdown();
    }

    private static boolean isProtoV3Enabled() {
        String property = System.getProperty(PROTO_V3_PROPERTY);
        if (property != null) return Boolean.parseBoolean(property);
        String env = System.getenv(PROTO_V3_ENV);
        if (env != null) return Boolean.parseBoolean(env);
        return true;
    }

    /**
     * Swaps .modl.gg ↔ .modl.top in the panel URL based on the testing API flag.
     */
    public static String adjustPanelUrlForEnv(String panelUrl, boolean useTestingApi) {
        if (panelUrl == null || panelUrl.isEmpty()) return panelUrl;
        if (useTestingApi) {
            return panelUrl.replace(".modl.gg", ".modl.top");
        } else {
            return panelUrl.replace(".modl.top", ".modl.gg");
        }
    }

    private static String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            String result = url;

            if (result.startsWith("https://")) {
                result = result.substring(8);
            } else if (result.startsWith("http://")) {
                result = result.substring(7);
            }

            int slashIndex = result.indexOf('/');
            if (slashIndex > 0) {
                result = result.substring(0, slashIndex);
            }

            return result;
        }
    }
}
