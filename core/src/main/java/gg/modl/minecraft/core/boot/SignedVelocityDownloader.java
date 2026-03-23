package gg.modl.minecraft.core.boot;

import gg.modl.minecraft.core.util.PluginLogger;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class SignedVelocityDownloader {
    private static final String VERSION = "1.4.1";
    private static final String BASE_URL = "https://github.com/4drian3d/SignedVelocity/releases/download/" + VERSION;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public enum Platform {
        PROXY("Proxy"),
        PAPER("Paper");

        private final String label;

        Platform(String label) {
            this.label = label;
        }
    }

    /**
     * Ensures the SignedVelocity JAR exists in the plugins folder.
     * Downloads from GitHub if missing. Returns path to JAR or null on failure.
     */
    public static Path ensureDownloaded(Platform platform, Path pluginsFolder, PluginLogger logger) {
        String fileName = "SignedVelocity-" + platform.label + "-" + VERSION + ".jar";
        Path target = pluginsFolder.resolve(fileName);

        if (Files.exists(target)) {
            return target;
        }

        String url = BASE_URL + "/" + fileName;
        logger.info("[SignedVelocity] Downloading " + fileName + "...");

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout((int) TIMEOUT.toMillis());
                connection.setReadTimeout((int) TIMEOUT.toMillis());
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "modl-minecraft");

                int statusCode = connection.getResponseCode();
                if (statusCode == 200) {
                    try (InputStream in = connection.getInputStream();
                         FileOutputStream out = new FileOutputStream(target.toFile())) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    logger.info("[SignedVelocity] Downloaded " + fileName + " successfully");
                    return target;
                } else {
                    logger.warning("[SignedVelocity] Download failed (HTTP " + statusCode + ")");
                    Files.deleteIfExists(target);
                    return null;
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            logger.warning("[SignedVelocity] Download failed: " + e.getMessage());
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignored) {}
            return null;
        }
    }
}
