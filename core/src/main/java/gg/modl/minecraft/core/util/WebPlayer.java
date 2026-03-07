package gg.modl.minecraft.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

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

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public static CompletableFuture<WebPlayer> get(String username) {
        return fromUrl(MOJANG_PROFILE_URL + username);
    }

    public static CompletableFuture<WebPlayer> get(UUID uuid) {
        return fromUrl(MOJANG_SESSION_URL + uuid.toString().replace("-", ""));
    }

    private static CompletableFuture<WebPlayer> fromUrl(String rawUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rawUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() != 200) {
                                logger.warning("Mojang API returned status " + response.statusCode() + " for URL: " + rawUrl);
                                return INVALID;
                            }

                            String jsonString = response.body();
                            if (jsonString == null || jsonString.trim().isEmpty()) {
                                logger.warning("Empty response from Mojang API for URL: " + rawUrl);
                                return INVALID;
                            }

                            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
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
                            com.google.gson.JsonArray propsArray = json.has("properties") ? json.getAsJsonArray("properties") : null;
                            if (propsArray != null && !propsArray.isEmpty()) {
                                JsonObject properties = propsArray.get(0).getAsJsonObject();
                                if (properties.has("value")) textureValue = properties.get("value").getAsString();
                            }

                            String skinId = getSkinId(json);

                            return new WebPlayer(name, playerUuid, skinId, textureValue, true);
                        } catch (Exception e) {
                            logger.warning("Error parsing Mojang API response for URL " + rawUrl + ": " + e.getMessage());
                            return INVALID;
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.warning("Failed to fetch player data from Mojang API for URL " + rawUrl + ": " + throwable.getMessage());
                        return INVALID;
                    });
        } catch (Exception e) {
            logger.warning("Error creating request for Mojang API URL " + rawUrl + ": " + e.getMessage());
            return CompletableFuture.completedFuture(INVALID);
        }
    }

    public static String getSkinId(JsonObject json) {
        try {
            com.google.gson.JsonArray propsArr = json.has("properties") ? json.getAsJsonArray("properties") : null;
            if (propsArr == null || propsArr.isEmpty()) return null;

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
            JsonObject decodedObject = JsonParser.parseString(decodedJson).getAsJsonObject();
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
            return get(username).get(SYNC_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warning("Synchronous WebPlayer.get() failed for username " + username + ": " + e.getMessage());
            return INVALID;
        }
    }

    @Deprecated
    public static WebPlayer getSync(UUID uuid) {
        try {
            return get(uuid).get(SYNC_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warning("Synchronous WebPlayer.get() failed for UUID " + uuid + ": " + e.getMessage());
            return INVALID;
        }
    }
}
