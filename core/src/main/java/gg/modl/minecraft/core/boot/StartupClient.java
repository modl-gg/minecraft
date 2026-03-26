package gg.modl.minecraft.core.boot;

import com.google.gson.Gson;
import gg.modl.minecraft.api.http.request.StartupRequest;
import gg.modl.minecraft.api.http.response.StartupResponse;
import gg.modl.minecraft.core.util.PluginLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class StartupClient {
    private static final String PROD_API_BASE = "https://api.modl.gg/v2/minecraft/startup";
    private static final String TEST_API_BASE = "https://api.modl.top/v2/minecraft/startup";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {2000, 4000, 8000};

    private StartupClient() {}

    public static String callStartupWithRetry(String apiKey, boolean testingApi,
                                               StartupRequest request, PluginLogger logger) {
        String url = testingApi ? TEST_API_BASE : PROD_API_BASE;
        Gson gson = new Gson();
        String jsonBody = gson.toJson(request);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String responseBody = sendPost(url, apiKey, jsonBody);
                StartupResponse response = gson.fromJson(responseBody, StartupResponse.class);
                if (response != null && response.getPanelUrl() != null) {
                    return response.getPanelUrl();
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

    private static String sendPost(String urlString, String apiKey, String jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout((int) TIMEOUT.toMillis());
            connection.setReadTimeout((int) TIMEOUT.toMillis());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-API-Key", apiKey);
            connection.setRequestProperty("User-Agent", "modl-minecraft");
            connection.setDoOutput(true);

            OutputStream os = connection.getOutputStream();
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();

            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException e) {
                String errorBody = readErrorStream(connection);
                if (errorBody != null) {
                    throw new IOException("Request to " + urlString + " failed: " + errorBody, e);
                }
                throw new IOException("Request to " + urlString + " failed: " + e.getMessage(), e);
            }

            java.io.InputStream stream = (status >= 200 && status < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                throw new IOException("Server returned HTTP " + status + " with no response body");
            }

            StringBuilder responseBody = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
            }

            if (status >= 400) {
                throw new IOException("Server returned HTTP " + status + ": " + responseBody);
            }

            return responseBody.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String readErrorStream(HttpURLConnection connection) {
        try {
            java.io.InputStream errorStream = connection.getErrorStream();
            if (errorStream == null) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
