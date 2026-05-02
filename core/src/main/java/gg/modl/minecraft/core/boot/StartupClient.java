package gg.modl.minecraft.core.boot;

import com.google.gson.Gson;
import gg.modl.minecraft.api.http.request.StartupRequest;
import gg.modl.minecraft.api.http.response.StartupResponse;
import gg.modl.minecraft.core.util.PluginLogger;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

public class StartupClient {
    private static final String PROD_API_BASE = "https://api.modl.gg/v2/minecraft/startup";
    private static final String TEST_API_BASE = "https://api.modl.top/v2/minecraft/startup";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {2000, 4000, 8000};
    private static volatile StartupResponse lastStartupResponse;

    private StartupClient() {}

    public static String callStartupWithRetry(String apiKey, boolean testingApi,
                                               StartupRequest request, PluginLogger logger) {
        StartupResponse response = callStartupForResponseWithRetry(apiKey, testingApi, request, logger);
        return response != null ? response.getPanelUrl() : null;
    }

    public static StartupResponse callStartupForResponseWithRetry(String apiKey, boolean testingApi,
                                                                  StartupRequest request, PluginLogger logger) {
        lastStartupResponse = null;
        String url = testingApi ? TEST_API_BASE : PROD_API_BASE;
        Gson gson = new Gson();
        String jsonBody = gson.toJson(request);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String responseBody = JsonPostSupport.postJson(url, TIMEOUT, jsonBody,
                        Collections.singletonMap("X-API-Key", apiKey));
                StartupResponse response = gson.fromJson(responseBody, StartupResponse.class);
                if (response != null && response.getPanelUrl() != null) {
                    lastStartupResponse = response;
                    return response;
                }
                logger.warning("Startup response missing panelUrl (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
            } catch (IOException e) {
                logger.warning("Startup request failed (attempt " + (attempt + 1) + "/" + MAX_RETRIES + "): " + e.getMessage());
            }

            if (attempt < MAX_RETRIES - 1) {
                try {
                    Thread.sleep(RETRY_DELAYS_MS[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        return null;
    }

    public static StartupResponse getLastStartupResponse() {
        return lastStartupResponse;
    }

    public static String getServerInstanceId() {
        StartupResponse response = lastStartupResponse;
        if (response == null) return null;
        String serverInstanceId = response.getServerInstanceId();
        return serverInstanceId != null && !serverInstanceId.trim().isEmpty()
                ? serverInstanceId.trim()
                : null;
    }
}
