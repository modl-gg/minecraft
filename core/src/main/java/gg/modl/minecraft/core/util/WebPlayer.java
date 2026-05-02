package gg.modl.minecraft.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import com.google.gson.JsonArray;
import java.util.concurrent.TimeUnit;

@Data @AllArgsConstructor
public class WebPlayer {
    private final String name;
    private final UUID uuid;
    private final String skin;
    private final String textureValue;
    private final boolean valid;

    private static final Logger logger = Logger.getLogger(WebPlayer.class.getName());
    private static final String UUID_REGEX = "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)";
    private static final String MOJANG_PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MOJANG_SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String TEXTURE_URL_PREFIX_HTTP = "http://textures.minecraft.net/texture/";
    private static final String TEXTURE_URL_PREFIX_HTTPS = "https://textures.minecraft.net/texture/";
    private static final WebPlayer INVALID = new WebPlayer(null, null, null, null, false);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long SYNC_TIMEOUT_MS = 10_000;

    public static CompletableFuture<WebPlayer> get(String username) {
        return fromUrl(MOJANG_PROFILE_URL + username);
    }

    public static CompletableFuture<WebPlayer> get(UUID uuid) {
        return fromUrl(MOJANG_SESSION_URL + uuid.toString().replace("-", ""));
    }

    private static CompletableFuture<WebPlayer> fromUrl(String rawUrl) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(rawUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
                    connection.setReadTimeout((int) REQUEST_TIMEOUT.toMillis());

                    int statusCode = connection.getResponseCode();
                    if (statusCode != 200) {
                        logger.warning("Mojang API returned status " + statusCode + " for URL: " + rawUrl);
                        return INVALID;
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
                        return INVALID;
                    }

                    JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
                    if (json == null) {
                        logger.warning("Invalid JSON response from Mojang API for URL: " + rawUrl);
                        return INVALID;
                    }

                    String name = json.has("name") ? json.get("name").getAsString() : null;
                    String idString = json.has("id") ? json.get("id").getAsString() : null;

                    if (name == null || idString == null) {
                        logger.warning("Missing name or id in Mojang API response for URL: " + rawUrl);
                        return INVALID;
                    }

                    UUID playerUuid = UUID.fromString(idString.replaceFirst(UUID_REGEX, "$1-$2-$3-$4-$5"));

                    String textureValue = null;
                    JsonArray propsArray = json.has("properties") ? json.getAsJsonArray("properties") : null;
                    if (propsArray != null && propsArray.size() > 0) {
                        JsonObject properties = propsArray.get(0).getAsJsonObject();
                        if (properties.has("value")) textureValue = properties.get("value").getAsString();
                    }

                    String skinId = getSkinId(json);

                    return new WebPlayer(name, playerUuid, skinId, textureValue, true);
                } catch (Exception e) {
                    logger.warning("Failed to fetch player data from Mojang API for URL " + rawUrl + ": " + e.getMessage());
                    return INVALID;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        } catch (Exception e) {
            logger.warning("Error creating request for Mojang API URL " + rawUrl + ": " + e.getMessage());
            return CompletableFuture.completedFuture(INVALID);
        }
    }

    public static String getSkinId(JsonObject json) {
        try {
            JsonArray propsArr = json.has("properties") ? json.getAsJsonArray("properties") : null;
            if (propsArr == null || propsArr.size() == 0) return null;

            JsonObject properties = propsArr.get(0).getAsJsonObject();
            if (!properties.has("value")) return null;

            return getSkinId(properties.get("value").getAsString());
        } catch (Exception e) {
            logger.warning("Error extracting skin ID from JSON: " + e.getMessage());
            return null;
        }
    }

    public static String getSkinId(String base64) {
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

    @Deprecated
    public static WebPlayer getSync(String username) {
        try {
            return get(username).get(SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warning("Synchronous WebPlayer.get() failed for username " + username + ": " + e.getMessage());
            return INVALID;
        }
    }

    @Deprecated
    public static WebPlayer getSync(UUID uuid) {
        try {
            return get(uuid).get(SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warning("Synchronous WebPlayer.get() failed for UUID " + uuid + ": " + e.getMessage());
            return INVALID;
        }
    }
}
