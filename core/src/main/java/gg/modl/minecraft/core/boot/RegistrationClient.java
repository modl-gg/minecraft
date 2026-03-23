package gg.modl.minecraft.core.boot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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

    public RegisterResponse register(String email, String serverName, String subdomain, String plan)
            throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("serverName", serverName);
        body.addProperty("customDomain", subdomain);
        body.addProperty("plan", plan != null ? plan : "free");

        String responseBody = sendPost(apiBase + "/cli", body.toString());
        return gson.fromJson(responseBody, RegisterResponse.class);
    }

    public CliStatusResponse pollCliStatus(String setupToken) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("token", setupToken);

        String responseBody = sendPost(apiBase + "/cli/status", body.toString());
        return gson.fromJson(responseBody, CliStatusResponse.class);
    }

    private String sendPost(String urlString, String jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout((int) TIMEOUT.toMillis());
            connection.setReadTimeout((int) TIMEOUT.toMillis());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "modl-minecraft");
            connection.setDoOutput(true);

            // Don't use try-with-resources: OutputStream.close() can internally call
            // getInputStream() on some JDK implementations, throwing an IOException
            // with the JDK default message format before we can read the error body.
            OutputStream os = connection.getOutputStream();
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();

            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException e) {
                // Some JDK versions re-throw from getResponseCode() if status line
                // is unavailable. Try to extract the error body for diagnostics.
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

    private String readErrorStream(HttpURLConnection connection) {
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
