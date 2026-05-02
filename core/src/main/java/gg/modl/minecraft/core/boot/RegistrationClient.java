package gg.modl.minecraft.core.boot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

public class RegistrationClient {
    private static final String PROD_API_BASE = "https://api.modl.gg/v1/public/registration";
    private static final String TEST_API_BASE = "https://api.modl.top/v1/public/registration";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Gson gson;
    private final String apiBase;

    public RegistrationClient(boolean testingApi) {
        this.gson = new Gson();
        this.apiBase = testingApi ? TEST_API_BASE : PROD_API_BASE;
    }

    public AvailabilityResponse checkAvailability(String email, String serverName, String subdomain)
            throws IOException {
        JsonObject body = new JsonObject();
        if (email != null && !email.isEmpty()) body.addProperty("email", email);
        if (serverName != null && !serverName.isEmpty()) body.addProperty("serverName", serverName);
        if (subdomain != null && !subdomain.isEmpty()) body.addProperty("customDomain", subdomain);

        String baseUrl = apiBase.replace("/registration", "/server");
        String responseBody = JsonPostSupport.postJson(
                baseUrl + "/check-availability", TIMEOUT, body.toString(), Collections.emptyMap());
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

    public static class AvailabilityResponse {
        private final boolean emailAvailable;
        private final boolean nameAvailable;
        private final boolean subdomainAvailable;
        private final String message;

        public AvailabilityResponse(boolean emailAvailable, boolean nameAvailable, boolean subdomainAvailable, String message) {
            this.emailAvailable = emailAvailable;
            this.nameAvailable = nameAvailable;
            this.subdomainAvailable = subdomainAvailable;
            this.message = message;
        }

        public boolean emailAvailable() { return emailAvailable; }
        public boolean nameAvailable() { return nameAvailable; }
        public boolean subdomainAvailable() { return subdomainAvailable; }
        public String message() { return message; }
    }

    public static class RegisterResponse {
        private final boolean success;
        private final String message;
        private final ServerInfo server;
        private final String setupToken;

        public RegisterResponse(boolean success, String message, ServerInfo server, String setupToken) {
            this.success = success;
            this.message = message;
            this.server = server;
            this.setupToken = setupToken;
        }

        public boolean success() { return success; }
        public String message() { return message; }
        public ServerInfo server() { return server; }
        public String setupToken() { return setupToken; }

        public static class ServerInfo {
            private final String id;
            private final String name;

            public ServerInfo(String id, String name) {
                this.id = id;
                this.name = name;
            }

            public String id() { return id; }
            public String name() { return name; }
        }
    }

    public static class CliStatusResponse {
        private final boolean success;
        private final Boolean emailVerified;
        private final String provisioningStatus;
        private final String apiKey;
        private final String message;

        public CliStatusResponse(boolean success, Boolean emailVerified, String provisioningStatus, String apiKey, String message) {
            this.success = success;
            this.emailVerified = emailVerified;
            this.provisioningStatus = provisioningStatus;
            this.apiKey = apiKey;
            this.message = message;
        }

        public boolean success() { return success; }
        public Boolean emailVerified() { return emailVerified; }
        public String provisioningStatus() { return provisioningStatus; }
        public String apiKey() { return apiKey; }
        public String message() { return message; }

        public boolean isComplete() {
            return apiKey != null && !apiKey.trim().isEmpty();
        }

        public boolean isFailed() {
            return "FAILED".equals(provisioningStatus);
        }
    }
}
