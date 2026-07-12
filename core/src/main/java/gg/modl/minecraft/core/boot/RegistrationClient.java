package gg.modl.minecraft.core.boot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

public class RegistrationClient {
    private static final String REGISTRATION_PATH = "/v1/public/registration";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Gson gson;
    private final String apiBase;

    public RegistrationClient(boolean testingApi) {
        this.gson = new Gson();
        this.apiBase = BackendHost.resolve(testingApi) + REGISTRATION_PATH;
    }

    public AvailabilityResponse checkAvailability(String email, String serverName, String subdomain)
            throws IOException {
        JsonObject body = new JsonObject();
        if (email != null && !email.isEmpty()) body.addProperty("email", email);
        if (serverName != null && !serverName.isEmpty()) body.addProperty("serverName", serverName);
        if (subdomain != null && !subdomain.isEmpty()) body.addProperty("customDomain", subdomain);

        String responseBody = JsonPostSupport.postJson(
                apiBase + "/check-availability", TIMEOUT, body.toString(), Collections.emptyMap());
        return gson.fromJson(responseBody, AvailabilityResponse.class);
    }

    public RegisterResponse register(String email, String serverName, String subdomain, String plan)
            throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("serverName", serverName);
        body.addProperty("customDomain", subdomain);
        body.addProperty("plan", plan != null ? plan : "free");

        String responseBody = JsonPostSupport.postJson(
                apiBase + "/cli", TIMEOUT, body.toString(), Collections.emptyMap());
        return gson.fromJson(responseBody, RegisterResponse.class);
    }

    public CliStatusResponse pollCliStatus(String setupToken) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("token", setupToken);

        String responseBody = JsonPostSupport.postJson(
                apiBase + "/cli/status", TIMEOUT, body.toString(), Collections.emptyMap());
        return gson.fromJson(responseBody, CliStatusResponse.class);
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class AvailabilityResponse {
        private boolean emailAvailable;
        private boolean nameAvailable;
        private boolean subdomainAvailable;
        private String message;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class RegisterResponse {
        private boolean success;
        private String message;
        private ServerInfo server;
        private String setupToken;

        @Getter @NoArgsConstructor @AllArgsConstructor
        public static class ServerInfo {
            private String id;
            private String name;
        }
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class CliStatusResponse {
        private boolean success;
        private Boolean emailVerified;
        private String provisioningStatus;
        private String apiKey;
        private String message;

        public boolean isComplete() {
            return apiKey != null && !apiKey.trim().isEmpty();
        }

        public boolean isFailed() {
            return "FAILED".equals(normalizeStatus(provisioningStatus));
        }

        public String humanReadableStatus() {
            String n = normalizeStatus(provisioningStatus);
            return n.isEmpty() ? "pending" : n.toLowerCase();
        }

        private static String normalizeStatus(String raw) {
            if (raw == null) return "";
            String s = raw.trim().toUpperCase();
            String prefix = "PROVISIONING_STATUS_";
            return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
        }
    }
}
