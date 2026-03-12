package gg.modl.minecraft.core.boot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RegistrationClient {
    private static final String API_BASE = "https://api.modl.gg/v1/public/registration";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Gson gson;

    public RegistrationClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.gson = new Gson();
    }

    public RegisterResponse register(String email, String serverName, String subdomain, String plan)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("serverName", serverName);
        body.addProperty("customDomain", subdomain);
        body.addProperty("plan", plan != null ? plan : "free");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/cli"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(TIMEOUT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), RegisterResponse.class);
    }

    public SetupStatusResponse pollSetupStatus(String token) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("token", token);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/setup-status"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(TIMEOUT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), SetupStatusResponse.class);
    }

    public ApiKeyResponse getApiKey(String autoLoginToken) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("token", autoLoginToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/api-key"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(TIMEOUT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), ApiKeyResponse.class);
    }

    public record RegisterResponse(boolean success, String message, ServerInfo server) {
        public record ServerInfo(String id, String name) {}
    }

    public record SetupStatusResponse(String subdomain, String serverName, Boolean emailVerified,
                                       String provisioningStatus, String message) {
        public boolean isComplete() {
            return "COMPLETED".equals(provisioningStatus);
        }

        public boolean isFailed() {
            return "FAILED".equals(provisioningStatus);
        }
    }

    public record ApiKeyResponse(boolean success, String apiKey, String panelUrl, String message) {}
}
