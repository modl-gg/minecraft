package gg.modl.minecraft.core.integration.mojang;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.core.util.BoundedLookupExecutor;
import gg.modl.minecraft.core.util.HttpConnectionOpener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class MojangProfileClient {
    private static final Logger logger = Logger.getLogger(MojangProfileClient.class.getName());
    private static final String UUID_REGEX = "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)";
    private static final String MOJANG_PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MOJANG_SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String TEXTURE_URL_PREFIX_HTTP = "http://textures.minecraft.net/texture/";
    private static final String TEXTURE_URL_PREFIX_HTTPS = "https://textures.minecraft.net/texture/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long SYNC_TIMEOUT_MS = 10_000;
    private static final int LOOKUP_QUEUE_CAPACITY = 64;

    private final BoundedLookupExecutor lookupExecutor =
            new BoundedLookupExecutor("modl-web-player", 1, 4, LOOKUP_QUEUE_CAPACITY, false);
    private final HttpConnectionOpener connectionOpener;

    public MojangProfileClient() {
        this(HttpConnectionOpener.SYSTEM);
    }

    public MojangProfileClient(HttpConnectionOpener connectionOpener) {
        this.connectionOpener = connectionOpener;
    }

    public CompletableFuture<WebPlayer> get(String username) {
        return fromUrl(MOJANG_PROFILE_URL + username);
    }

    public CompletableFuture<WebPlayer> get(UUID uuid) {
        if (!isMojangAccountUuid(uuid)) return CompletableFuture.completedFuture(WebPlayer.invalid());
        return fromUrl(MOJANG_SESSION_URL + uuid.toString().replace("-", ""));
    }

    public WebPlayer getSync(String username) {
        try {
            return get(username).get(SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warning("Synchronous MojangProfileClient.get() failed for username " + username + ": " + e.getMessage());
            return WebPlayer.invalid();
        }
    }

    public WebPlayer getSync(UUID uuid) {
        try {
            return get(uuid).get(SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warning("Synchronous MojangProfileClient.get() failed for UUID " + uuid + ": " + e.getMessage());
            return WebPlayer.invalid();
        }
    }

    public void shutdown() {
        lookupExecutor.shutdown();
    }

    private CompletableFuture<WebPlayer> fromUrl(String rawUrl) {
        try {
            return lookupExecutor.supplyAsync(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(rawUrl);
                    connection = connectionOpener.open(url);
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
                    connection.setReadTimeout((int) REQUEST_TIMEOUT.toMillis());

                    int statusCode = connection.getResponseCode();
                    if (statusCode == 204 || statusCode == 404) {
                        logger.fine("No Mojang profile found for URL: " + rawUrl);
                        return WebPlayer.invalid();
                    }
                    if (statusCode != 200) {
                        logger.warning("Mojang API returned status " + statusCode + " for URL: " + rawUrl);
                        return WebPlayer.invalid();
                    }

                    StringBuilder responseBody = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBody.append(line);
                        }
                    }

                    String jsonString = responseBody.toString();
                    if (jsonString.trim().isEmpty()) {
                        logger.warning("Empty response from Mojang API for URL: " + rawUrl);
                        return WebPlayer.invalid();
                    }

                    JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
                    if (json == null) {
                        logger.warning("Invalid JSON response from Mojang API for URL: " + rawUrl);
                        return WebPlayer.invalid();
                    }

                    String name = json.has("name") ? json.get("name").getAsString() : null;
                    String idString = json.has("id") ? json.get("id").getAsString() : null;

                    if (name == null || idString == null) {
                        logger.warning("Missing name or id in Mojang API response for URL: " + rawUrl);
                        return WebPlayer.invalid();
                    }

                    UUID playerUuid = UUID.fromString(idString.replaceFirst(UUID_REGEX, "$1-$2-$3-$4-$5"));

                    String textureValue = null;
                    JsonArray propsArray = json.has("properties") ? json.getAsJsonArray("properties") : null;
                    if (propsArray != null && propsArray.size() > 0) {
                        JsonObject properties = propsArray.get(0).getAsJsonObject();
                        if (properties.has("value")) textureValue = properties.get("value").getAsString();
                    }

                    String skinId = extractSkinId(json);

                    return new WebPlayer(name, playerUuid, skinId, textureValue, true);
                } catch (Exception e) {
                    logger.warning("Failed to fetch player data from Mojang API for URL " + rawUrl + ": " + e.getMessage());
                    return WebPlayer.invalid();
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        } catch (Exception e) {
            logger.warning("Error creating request for Mojang API URL " + rawUrl + ": " + e.getMessage());
            return CompletableFuture.completedFuture(WebPlayer.invalid());
        }
    }

    private static boolean isMojangAccountUuid(UUID uuid) {
        return uuid.version() == 4;
    }

    private static String extractSkinId(JsonObject json) {
        try {
            JsonArray propsArr = json.has("properties") ? json.getAsJsonArray("properties") : null;
            if (propsArr == null || propsArr.size() == 0) return null;

            JsonObject properties = propsArr.get(0).getAsJsonObject();
            if (!properties.has("value")) return null;

            return decodeSkinId(properties.get("value").getAsString());
        } catch (Exception e) {
            logger.warning("Error extracting skin ID from JSON: " + e.getMessage());
            return null;
        }
    }

    private static String decodeSkinId(String base64) {
        try {
            if (base64 == null || base64.trim().isEmpty()) return null;

            String decodedJson = new String(Base64.getDecoder().decode(base64));
            JsonObject decodedObject = new JsonParser().parse(decodedJson).getAsJsonObject();
            if (!decodedObject.has("textures")) return null;

            JsonObject textures = decodedObject.getAsJsonObject("textures");
            if (!textures.has("SKIN")) return null;

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (!skin.has("url")) return null;

            String url = skin.get("url").getAsString();
            return url.replace(TEXTURE_URL_PREFIX_HTTP, "")
                    .replace(TEXTURE_URL_PREFIX_HTTPS, "");
        } catch (Exception e) {
            logger.warning("Error extracting skin ID from base64: " + e.getMessage());
            return null;
        }
    }
}
