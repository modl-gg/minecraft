package gg.modl.minecraft.bridge.reporter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class ModlBackendReplayUploader {

    private final String backendUrl;
    private final String apiKey;
    private final String serverDomain;
    private final Logger logger;
    private final Gson gson;

    private static final String USER_AGENT = "modl-minecraft";
    private static final int CONNECT_TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();
    private static final int READ_TIMEOUT_MS = (int) Duration.ofSeconds(30).toMillis();
    private static final int UPLOAD_READ_TIMEOUT_MS = (int) Duration.ofMinutes(5).toMillis();

    public ModlBackendReplayUploader(String backendUrl, String apiKey, String serverDomain, Logger logger) {
        this.backendUrl = backendUrl;
        this.apiKey = apiKey;
        this.serverDomain = serverDomain;
        this.logger = logger;
        this.gson = new Gson();
    }

    public CompletableFuture<String> uploadAsync(File replayFile, String mcVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InitResponse init = initUpload(replayFile, mcVersion);
                uploadToStorage(replayFile, init.uploadUrl);
                confirmUpload(init.replayId);
                return init.replayId;
            } catch (Exception e) {
                throw new RuntimeException("Replay upload failed: " + e.getMessage(), e);
            }
        });
    }

    private InitResponse initUpload(File file, String mcVersion) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("mcVersion", mcVersion);
        body.addProperty("fileSize", file.length());

        HttpURLConnection connection = (HttpURLConnection) new URL(backendUrl + "/v1/minecraft/replays/upload").openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("X-API-Key", apiKey);
            connection.setRequestProperty("X-Server-Domain", serverDomain);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection);

            if (statusCode != 200) {
                throw new RuntimeException("Init upload failed (HTTP " + statusCode + "): " + responseBody);
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            if (json == null || !json.has("replayId") || !json.has("uploadUrl")) {
                throw new RuntimeException("Malformed init response: " + responseBody);
            }

            return new InitResponse(
                    json.get("replayId").getAsString(),
                    json.get("uploadUrl").getAsString()
            );
        } finally {
            connection.disconnect();
        }
    }

    private void uploadToStorage(File file, String presignedUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(presignedUrl).openConnection();
        try {
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(UPLOAD_READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setDoOutput(true);

            byte[] fileBytes = Files.readAllBytes(file.toPath());
            try (OutputStream os = connection.getOutputStream()) {
                os.write(fileBytes);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String responseBody = readResponseBody(connection);
                throw new RuntimeException("Storage upload failed (HTTP " + statusCode + "): " + responseBody);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void confirmUpload(String replayId) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(backendUrl + "/v1/minecraft/replays/confirm/" + replayId).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("X-API-Key", apiKey);
            connection.setRequestProperty("X-Server-Domain", serverDomain);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(new byte[0]);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                String responseBody = readResponseBody(connection);
                throw new RuntimeException("Confirm upload failed (HTTP " + statusCode
                        + ") for replay " + replayId + ": " + responseBody);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String readResponseBody(HttpURLConnection connection) {
        try {
            java.io.InputStream stream = connection.getResponseCode() >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static class InitResponse {
        final String replayId;
        final String uploadUrl;

        InitResponse(String replayId, String uploadUrl) {
            this.replayId = replayId;
            this.uploadUrl = uploadUrl;
        }
    }
}
