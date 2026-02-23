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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class UpdateCheckerService {
    private static final String RELEASES_API_URL = "https://api.github.com/repos/modl-gg/minecraft/releases/latest";
    private static final String RELEASES_PAGE_URL = "https://github.com/modl-gg/minecraft/releases";
    private static final int DEFAULT_INTERVAL_MINUTES = 60;
    private static final int MIN_INTERVAL_MINUTES = 1;

    private final Logger logger;
    private final boolean debugMode;
    private final String currentVersion;
    private final HttpClient httpClient;
    private final Gson gson;

    private ScheduledExecutorService scheduler;

    public UpdateCheckerService(Logger logger, boolean debugMode, String currentVersion) {
        this.logger = logger;
        this.debugMode = debugMode;
        this.currentVersion = currentVersion;
        this.gson = new Gson();

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "modl-update-checker-http");
            thread.setDaemon(true);
            return thread;
        };

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .executor(Executors.newCachedThreadPool(threadFactory))
                .build();
    }

    public synchronized void start(boolean enabled, int intervalMinutes) {
        stop();

        if (!enabled) {
            if (debugMode) {
                logger.info("[Update Checker] Disabled in config.");
            }
            return;
        }

        int effectiveIntervalMinutes = Math.max(MIN_INTERVAL_MINUTES, intervalMinutes);
        if (effectiveIntervalMinutes != intervalMinutes && debugMode) {
            logger.info("[Update Checker] Interval adjusted to minimum of " + MIN_INTERVAL_MINUTES + " minute(s).");
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modl-update-checker");
            thread.setDaemon(true);
            return thread;
        });

        // Startup check first, then repeat on configured interval.
        scheduler.scheduleWithFixedDelay(this::checkSafely, 0, effectiveIntervalMinutes, TimeUnit.MINUTES);
    }

    public synchronized void reload(boolean enabled, int intervalMinutes) {
        start(enabled, intervalMinutes);
    }

    public synchronized void stop() {
        if (scheduler == null) {
            return;
        }

        scheduler.shutdownNow();
        scheduler = null;
    }

    private void checkSafely() {
        try {
            ReleaseInfo latest = fetchLatestRelease();
            if (latest == null || latest.tagName == null || latest.tagName.isEmpty()) {
                return;
            }

            if (compareVersions(currentVersion, latest.tagName) < 0) {
                logger.warning("[MODL] Update available: current=" + currentVersion + ", latest=" + latest.tagName);
                logger.warning("[MODL] Download: " + latest.downloadUrl);
            }
        } catch (Exception exception) {
            if (debugMode) {
                logger.info("[Update Checker] Failed to check for updates: " + exception.getMessage());
            }
        }
    }

    private ReleaseInfo fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RELEASES_API_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "modl-minecraft/" + currentVersion)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            if (debugMode) {
                logger.info("[Update Checker] GitHub API returned status " + response.statusCode());
            }
            return null;
        }

        try {
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (json == null || !json.has("tag_name")) {
                return null;
            }

            String tagName = json.get("tag_name").getAsString();
            String htmlUrl = RELEASES_PAGE_URL;
            if (json.has("html_url") && !json.get("html_url").isJsonNull()) {
                htmlUrl = json.get("html_url").getAsString();
            }

            return new ReleaseInfo(tagName, htmlUrl);
        } catch (JsonSyntaxException exception) {
            if (debugMode) {
                logger.info("[Update Checker] Failed to parse GitHub response: " + exception.getMessage());
            }
            return null;
        }
    }

    private int compareVersions(String current, String latest) {
        int[] currentParts = parseVersionParts(current);
        int[] latestParts = parseVersionParts(latest);

        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int left = i < currentParts.length ? currentParts[i] : 0;
            int right = i < latestParts.length ? latestParts[i] : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }

    private int[] parseVersionParts(String rawVersion) {
        String normalized = normalizeVersion(rawVersion);
        if (normalized.isEmpty()) {
            return new int[] {0, 0, 0};
        }

        String[] split = normalized.split("\\.");
        int[] parts = new int[Math.max(split.length, 3)];

        for (int i = 0; i < split.length; i++) {
            parts[i] = parseLeadingNumber(split[i]);
        }

        return parts;
    }

    private String normalizeVersion(String rawVersion) {
        if (rawVersion == null) {
            return "";
        }

        String version = rawVersion.trim();
        if (version.isEmpty()) {
            return "";
        }

        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }

        int dashIndex = version.indexOf('-');
        if (dashIndex >= 0) {
            version = version.substring(0, dashIndex);
        }

        int plusIndex = version.indexOf('+');
        if (plusIndex >= 0) {
            version = version.substring(0, plusIndex);
        }

        return version.trim();
    }

    private int parseLeadingNumber(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }

        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }

        if (digits.isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return 0;
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