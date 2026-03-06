package gg.modl.minecraft.core.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class UpdateCheckerService {
    private static final String RELEASES_API_URL = "https://api.github.com/repos/modl-gg/minecraft/releases/latest";
    private static final String RELEASES_PAGE_URL = "https://github.com/modl-gg/minecraft/releases";
    private static final int DEFAULT_INTERVAL_MINUTES = 60;
    private static final int MIN_INTERVAL_MINUTES = 1;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final Logger logger;
    private final boolean debugMode;
    private final String currentVersion;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    private volatile ScheduledExecutorService scheduler;
    private volatile boolean isFirstRun = true;

    public UpdateCheckerService(Logger logger, boolean debugMode, String currentVersion) {
        this.logger = logger;
        this.debugMode = debugMode;
        this.currentVersion = currentVersion;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "modl-update-checker-http");
                    t.setDaemon(true);
                    return t;
                }))
                .build();
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
                logger.warning("[modl.gg] Update available: current=" + currentVersion + ", latest=" + latest.tagName);
                logger.warning("[modl.gg] Download: " + latest.downloadUrl);
            } else if (isFirstRun) {
                logger.info("[modl.gg] You are up to date! (" + latest.tagName + ")");
            }
            isFirstRun = false;
        } catch (Exception e) {
            if (debugMode) logger.info("[Update Checker] Failed to check for updates: " + e.getMessage());
        }
    }

    private ReleaseInfo fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RELEASES_API_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "modl-minecraft/" + currentVersion)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            if (debugMode) logger.info("[Update Checker] GitHub API returned status " + response.statusCode());
            return null;
        }

        try {
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (json == null || !json.has("tag_name")) return null;

            String tagName = json.get("tag_name").getAsString();
            String htmlUrl = RELEASES_PAGE_URL;
            if (json.has("html_url") && !json.get("html_url").isJsonNull()) htmlUrl = json.get("html_url").getAsString();
            return new ReleaseInfo(tagName, htmlUrl);
        } catch (JsonSyntaxException e) {
            if (debugMode) logger.info("[Update Checker] Failed to parse GitHub response: " + e.getMessage());
            return null;
        }
    }

    public static int getDefaultIntervalMinutes() {
        return DEFAULT_INTERVAL_MINUTES;
    }

    private static final class ReleaseInfo {
        private final String tagName;
        private final String downloadUrl;

        private ReleaseInfo(String tagName, String downloadUrl) {
            this.tagName = tagName;
            this.downloadUrl = downloadUrl == null || downloadUrl.isBlank() ? RELEASES_PAGE_URL : downloadUrl;
        }
    }
}