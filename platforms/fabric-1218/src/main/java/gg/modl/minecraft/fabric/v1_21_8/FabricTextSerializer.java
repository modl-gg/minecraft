package gg.modl.minecraft.fabric.v1_21_8;

import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.core.util.PluginLogger;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

@RequiredArgsConstructor
public class FabricTextSerializer {
    private static final Gson GSON = new Gson();

    private final PluginLogger logger;

    public void sendLegacyMessage(ServerPlayerEntity player, String message) {
        player.sendMessage(parseLegacyText(player, message), false);
    }

    public Text parseLegacyText(ServerPlayerEntity player, String message) {
        String normalizedMessage = message == null ? "" : message;
        try {
            String json = AdventureSerializer.toJson(CirrusChatElement.ofLegacyText(normalizedMessage).asComponent());
            Text text = TextCodecs.CODEC.parse(
                    RegistryOps.of(JsonOps.INSTANCE, player.getRegistryManager()),
                    JsonParser.parseString(json)).result().orElse(null);
            if (text != null) {
                return text;
            }
        } catch (Exception e) {
            logger.debug("Failed to parse legacy text: " + e.getMessage());
        }
        return Text.literal(stripAmpersandAndSection(normalizedMessage));
    }

    public void sendInterceptMessage(ServerPlayerEntity player, String message) {
        try {
            String json = AdventureSerializer.toJson(CirrusChatElement.ofLegacyText(message).asComponent());
            Text text = TextCodecs.CODEC.parse(
                    RegistryOps.of(JsonOps.INSTANCE, player.getRegistryManager()),
                    JsonParser.parseString(json)).result().orElse(null);
            if (text != null) {
                player.sendMessage(text, false);
                return;
            }
        } catch (Exception e) {
            logger.debug("Failed to serialize intercept message: " + e.getMessage());
        }
        player.sendMessage(Text.literal(stripSection(message)), false);
    }

    public void sendJsonToPlayer(ServerPlayerEntity player, String jsonMessage) {
        try {
            JsonElement element = JsonParser.parseString(jsonMessage);
            fixLegacyHoverEvents(element);
            String fixedJson = GSON.toJson(element);
            Component component = AdventureSerializer.serializer().fromJson(fixedJson);
            String normalizedJson = AdventureSerializer.toJson(component);
            JsonElement jsonElement = JsonParser.parseString(normalizedJson);
            Text text = TextCodecs.CODEC.parse(
                    RegistryOps.of(JsonOps.INSTANCE, player.getRegistryManager()),
                    jsonElement).result().orElse(null);
            if (text != null) {
                player.sendMessage(text, false);
                return;
            }
        } catch (Exception e) {
            logger.warning("Failed to parse JSON message: " + e.getMessage());
        }
        player.sendMessage(Text.literal(stripAmpersandAndSection(jsonMessage)), false);
    }

    private void fixLegacyHoverEvents(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                fixLegacyHoverEvents(child);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("hoverEvent") && object.get("hoverEvent").isJsonObject()) {
            JsonObject hover = object.getAsJsonObject("hoverEvent");
            if (hover != null && hover.has("value") && !hover.has("contents")) {
                JsonElement value = hover.remove("value");
                if (value.isJsonPrimitive()) {
                    JsonObject contents = new JsonObject();
                    contents.add("text", value);
                    hover.add("contents", contents);
                } else {
                    hover.add("contents", value);
                }
            }
        }

        for (String key : object.keySet()) {
            fixLegacyHoverEvents(object.get(key));
        }
    }

    private String stripAmpersandAndSection(String message) {
        return (message == null ? "" : message).replaceAll("(?i)[&\u00a7][0-9a-fk-or]", "");
    }

    private String stripSection(String message) {
        return (message == null ? "" : message).replaceAll("\u00a7[0-9a-fk-or]", "");
    }
}
