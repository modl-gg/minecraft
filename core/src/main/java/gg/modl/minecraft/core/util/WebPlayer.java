package gg.modl.minecraft.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;

public record WebPlayer(String name, UUID uuid, String skin, boolean valid) {
    private static final String REGEX_PATTERN = "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)";

    public static WebPlayer get(String username) throws IOException {
        return fromUrl("https://api.mojang.com/users/profiles/minecraft/" + username);
    }

    public static WebPlayer get(UUID uuid) throws IOException {
        return fromUrl("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
    }

    private static WebPlayer fromUrl(String rawUrl) throws IOException {
        URL url = new URL(rawUrl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setHostnameVerifier((hostname, session) -> true);
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        StringBuilder stringBuilder = new StringBuilder();
        int cp;
        while ((cp = reader.read()) != -1) stringBuilder.append((char) cp);

        String jsonString = stringBuilder.toString();
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        if (json == null) return null;

        return new WebPlayer(json.get("name").getAsString(),
                UUID.fromString(json.get("id").getAsString().replaceFirst(REGEX_PATTERN,
                        "$1-$2-$3-$4-$5")), getSkinId(json), true);
    }

    public static String getSkinId(JsonObject json) {
        JsonObject properties = json.getAsJsonArray("properties").get(0).getAsJsonObject();
        return getSkinId(properties.get("value").getAsString());
    }

    public static String getSkinId(String base64) {
        // Decode the base64 value
        String decodedJson = new String(Base64.getDecoder().decode(base64));
        JsonObject decodedObject = JsonParser.parseString(decodedJson).getAsJsonObject();

        // Extract the textures.minecraft.net link
        return decodedObject.getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url")
                .getAsString().replace("http://textures.minecraft.net/texture/", "");

    }
}
