package gg.modl.minecraft.core.boot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class JsonPostSupport {
    private JsonPostSupport() {}

    static String postJson(String urlString, Duration timeout, String jsonBody, Map<String, String> extraHeaders)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout((int) timeout.toMillis());
            connection.setReadTimeout((int) timeout.toMillis());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "modl-minecraft");
            for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            connection.setDoOutput(true);

            writeBodyWithoutAutoClose(connection, jsonBody);

            int status = readResponseCodeOrExtractError(connection, urlString);

            InputStream stream = (status >= 200 && status < 300)
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

    private static void writeBodyWithoutAutoClose(HttpURLConnection connection, String jsonBody) throws IOException {
        OutputStream os = connection.getOutputStream();
        os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private static int readResponseCodeOrExtractError(HttpURLConnection connection, String urlString) throws IOException {
        try {
            return connection.getResponseCode();
        } catch (IOException e) {
            String errorBody = readErrorStream(connection);
            if (errorBody != null) {
                throw new IOException("Request to " + urlString + " failed: " + errorBody, e);
            }
            throw new IOException("Request to " + urlString + " failed: " + e.getMessage(), e);
        }
    }

    private static String readErrorStream(HttpURLConnection connection) {
        try {
            InputStream errorStream = connection.getErrorStream();
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
