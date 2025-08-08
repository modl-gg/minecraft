package gg.modl.minecraft.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public record WebPlayer(String name, UUID uuid, String skin, boolean valid) {
    private static final Logger logger = Logger.getLogger(WebPlayer.class.getName());
    private static final String REGEX_PATTERN = "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)";

    // Shared HTTP client for better performance
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Get player information by username asynchronously
     */
    public static CompletableFuture<WebPlayer> get(String username) {
        return fromUrl("https://api.mojang.com/users/profiles/minecraft/" + username);
    }

    /**
     * Get player information by UUID asynchronously
     */
    public static CompletableFuture<WebPlayer> get(UUID uuid) {
        return fromUrl("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
    }

    /**
     * Fetch player data from URL asynchronously
     */
    private static CompletableFuture<WebPlayer> fromUrl(String rawUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rawUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() != 200) {
                                logger.warning("Mojang API returned status " + response.statusCode() + " for URL: " + rawUrl);
                                return new WebPlayer(null, null, null, false);
                            }

                            String jsonString = response.body();
                            if (jsonString == null || jsonString.trim().isEmpty()) {
                                logger.warning("Empty response from Mojang API for URL: " + rawUrl);
                                return new WebPlayer(null, null, null, false);
                            }

                            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
                            if (json == null) {
                                logger.warning("Invalid JSON response from Mojang API for URL: " + rawUrl);
                                return new WebPlayer(null, null, null, false);
                            }

                            String name = json.has("name") ? json.get("name").getAsString() : null;
                            String idString = json.has("id") ? json.get("id").getAsString() : null;

                            if (name == null || idString == null) {
                                logger.warning("Missing name or id in Mojang API response for URL: " + rawUrl);
                                return new WebPlayer(null, null, null, false);
                            }

                            UUID playerUuid = UUID.fromString(idString.replaceFirst(REGEX_PATTERN, "$1-$2-$3-$4-$5"));
                            String skinId = getSkinId(json);

                            return new WebPlayer(name, playerUuid, skinId, true);
                        } catch (Exception e) {
                            logger.warning("Error parsing Mojang API response for URL " + rawUrl + ": " + e.getMessage());
                            return new WebPlayer(null, null, null, false);
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.warning("Failed to fetch player data from Mojang API for URL " + rawUrl + ": " + throwable.getMessage());
                        return new WebPlayer(null, null, null, false);
                    });
        } catch (Exception e) {
            logger.warning("Error creating request for Mojang API URL " + rawUrl + ": " + e.getMessage());
            return CompletableFuture.completedFuture(new WebPlayer(null, null, null, false));
        }
    }

    /**
     * Extract skin ID from Mojang API response
     */
    public static String getSkinId(JsonObject json) {
        try {
            if (!json.has("properties") || json.getAsJsonArray("properties").size() == 0) {
                return null;
            }

            JsonObject properties = json.getAsJsonArray("properties").get(0).getAsJsonObject();
            if (!properties.has("value")) {
                return null;
            }

            return getSkinId(properties.get("value").getAsString());
        } catch (Exception e) {
            logger.warning("Error extracting skin ID from JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract skin ID from base64 encoded texture data
     */
    public static String getSkinId(String base64) {
        try {
            if (base64 == null || base64.trim().isEmpty()) {
                return null;
            }

            // Decode the base64 value
            String decodedJson = new String(Base64.getDecoder().decode(base64));
            JsonObject decodedObject = JsonParser.parseString(decodedJson).getAsJsonObject();

            // Extract the textures.minecraft.net link
            if (!decodedObject.has("textures")) {
                return null;
            }

            JsonObject textures = decodedObject.getAsJsonObject("textures");
            if (!textures.has("SKIN")) {
                return null;
            }

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (!skin.has("url")) {
                return null;
            }

            String url = skin.get("url").getAsString();
            return url.replace("http://textures.minecraft.net/texture/", "")
                     .replace("https://textures.minecraft.net/texture/", "");
        } catch (Exception e) {
            logger.warning("Error extracting skin ID from base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Synchronous wrapper for backward compatibility (deprecated)
     * @deprecated Use the async get() methods instead
     */
    @Deprecated
    public static WebPlayer getSync(String username) {
        try {
            return get(username).get(java.util.concurrent.TimeUnit.SECONDS.toMillis(10),
                                   java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warning("Synchronous WebPlayer.get() failed for username " + username + ": " + e.getMessage());
            return new WebPlayer(null, null, null, false);
        }
    }

    /**
     * Synchronous wrapper for backward compatibility (deprecated)
     * @deprecated Use the async get() methods instead
     */
    @Deprecated
    public static WebPlayer getSync(UUID uuid) {
        try {
            return get(uuid).get(java.util.concurrent.TimeUnit.SECONDS.toMillis(10),
                                java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warning("Synchronous WebPlayer.get() failed for UUID " + uuid + ": " + e.getMessage());
            return new WebPlayer(null, null, null, false);
        }
    }
}
