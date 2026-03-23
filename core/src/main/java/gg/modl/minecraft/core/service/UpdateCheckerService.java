package gg.modl.minecraft.core.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import gg.modl.minecraft.core.util.PluginLogger;

public class UpdateCheckerService {
    private static final String RELEASES_API_URL = "https://api.github.com/repos/modl-gg/minecraft/releases/latest",
            RELEASES_PAGE_URL = "https://github.com/modl-gg/minecraft/releases";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8), REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_INTERVAL_MINUTES = 60, MIN_INTERVAL_MINUTES = 1;

    private final PluginLogger logger;
    private final String currentVersion;
    private final Gson gson = new Gson();

    private volatile ScheduledExecutorService scheduler;
    private final boolean debugMode;
    private volatile boolean isFirstRun = true;

    public UpdateCheckerService(PluginLogger logger, boolean debugMode, String currentVersion) {
        this.logger = logger;
        this.debugMode = debugMode;
        this.currentVersion = currentVersion;
    }

    public synchronized void start(boolean enabled, int intervalMinutes) {
        stop();
        if (!enabled) {
            if (debugMode) logger.info("[Update Checker] Disabled in config.");
            return;
        }

        int effectiveInterval = Math.max(MIN_INTERVAL_MINUTES, intervalMinutes);
        if (effectiveInterval != intervalMinutes && debugMode) {
            logger.info("[Update Checker] Interval adjusted to minimum of " + MIN_INTERVAL_MINUTES + " minute(s).");
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-update-checker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::checkSafely, 0, effectiveInterval, TimeUnit.MINUTES);
    }

    public synchronized void reload(boolean enabled, int intervalMinutes) {
        start(enabled, intervalMinutes);
    }

    public synchronized void stop() {
        if (scheduler == null) return;
        scheduler.shutdownNow();
        scheduler = null;
    }

    private void checkSafely() {
        try {
            ReleaseInfo latest = fetchLatestRelease();
            if (latest == null || latest.tagName == null || latest.tagName.isEmpty()) return;

            if (!currentVersion.equalsIgnoreCase(latest.tagName)) {
                logger.warning("Update available: current=" + currentVersion + ", latest=" + latest.tagName);
                logger.warning("Download: " + latest.downloadUrl);
            } else if (isFirstRun) {
                logger.info("You are up to date! (" + latest.tagName + ")");
            }
            isFirstRun = false;
        } catch (Exception e) {
            if (debugMode) logger.info("[Update Checker] Failed to check for updates: " + e.getMessage());
        }
    }

    private ReleaseInfo fetchLatestRelease() throws IOException {
        URL url = new URL(RELEASES_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
            connection.setReadTimeout((int) REQUEST_TIMEOUT.toMillis());
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "modl-minecraft/" + currentVersion);

            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                if (debugMode) logger.info("[Update Checker] GitHub API returned status " + statusCode);
                return null;
            }

            StringBuilder responseBody = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
            }

            try {
                JsonObject json = gson.fromJson(responseBody.toString(), JsonObject.class);
                if (json == null || !json.has("tag_name")) return null;

                String tagName = json.get("tag_name").getAsString();
                String htmlUrl = RELEASES_PAGE_URL;
                if (json.has("html_url") && !json.get("html_url").isJsonNull()) htmlUrl = json.get("html_url").getAsString();
                return new ReleaseInfo(tagName, htmlUrl);
            } catch (JsonSyntaxException e) {
                if (debugMode) logger.info("[Update Checker] Failed to parse GitHub response: " + e.getMessage());
                return null;
            }
        } finally {
            connection.disconnect();
        }
    }

    public static int getDefaultIntervalMinutes() {
        return DEFAULT_INTERVAL_MINUTES;
    }

    private static final class ReleaseInfo {
        private final String tagName, downloadUrl;

        private ReleaseInfo(String tagName, String downloadUrl) {
            this.tagName = tagName;
            this.downloadUrl = downloadUrl == null || downloadUrl.trim().isEmpty() ? RELEASES_PAGE_URL : downloadUrl;
        }
    }
}
