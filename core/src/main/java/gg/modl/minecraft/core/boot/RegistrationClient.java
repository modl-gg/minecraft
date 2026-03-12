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
    private static final String PROD_API_BASE = "https://api.modl.gg/v1/public/registration";
    private static final String TEST_API_BASE = "https://api.modl.top/v1/public/registration";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Gson gson;
    private final String apiBase;

    public RegistrationClient(boolean testingApi) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.gson = new Gson();
        this.apiBase = testingApi ? TEST_API_BASE : PROD_API_BASE;
    }

    public RegisterResponse register(String email, String serverName, String subdomain, String plan)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("serverName", serverName);
        body.addProperty("customDomain", subdomain);
        body.addProperty("plan", plan != null ? plan : "free");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/cli"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(TIMEOUT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), RegisterResponse.class);
    }

    public CliStatusResponse pollCliStatus(String setupToken) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("token", setupToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/cli/status"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(TIMEOUT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return gson.fromJson(response.body(), CliStatusResponse.class);
    }

    public record RegisterResponse(boolean success, String message, ServerInfo server, String setupToken) {
        public record ServerInfo(String id, String name) {}
    }

    public record CliStatusResponse(boolean success, Boolean emailVerified, String provisioningStatus,
                                     String apiKey, String message) {
        public boolean isComplete() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean isFailed() {
            return "FAILED".equals(provisioningStatus);
        }
    }
}
