package gg.modl.minecraft.spigot.bridge.reporter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Uploads replay files to modl-backend (not replay-lite-backend).
 * Uses the same API key the bridge already has for modl-backend.
 * Returns a replayId (UUID) instead of a viewer URL.
 */
public class ModlBackendReplayUploader {

    private final String backendUrl;
    private final String apiKey;
    private final String serverDomain;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Gson gson;

    public ModlBackendReplayUploader(String backendUrl, String apiKey, String serverDomain, Logger logger) {
        this.backendUrl = backendUrl;
        this.apiKey = apiKey;
        this.serverDomain = serverDomain;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    /**
     * Uploads a replay file to modl-backend.
     * Returns the replayId (UUID string) on success.
     */
    public CompletableFuture<String> uploadAsync(File replayFile, String mcVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Init upload — get presigned URL
                InitResponse init = initUpload(replayFile, mcVersion);

                // Step 2: PUT file to presigned URL
                uploadToStorage(replayFile, init.uploadUrl);

                // Step 3: Confirm upload
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/v1/minecraft/replays/upload"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .header("X-Server-Domain", serverDomain)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Init upload failed (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("replayId") || !json.has("uploadUrl")) {
            throw new RuntimeException("Malformed init response: " + response.body());
        }

        return new InitResponse(
                json.get("replayId").getAsString(),
                json.get("uploadUrl").getAsString()
        );
    }

    private void uploadToStorage(File file, String presignedUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(presignedUrl))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(Path.of(file.getAbsolutePath())))
                .timeout(Duration.ofMinutes(5))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Storage upload failed (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    private void confirmUpload(String replayId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + "/v1/minecraft/replays/confirm/" + replayId))
                .header("X-API-Key", apiKey)
                .header("X-Server-Domain", serverDomain)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Confirm upload failed (HTTP " + response.statusCode()
                    + ") for replay " + replayId + ": " + response.body());
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
