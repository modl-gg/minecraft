package gg.modl.minecraft.fabric.v26;

import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.core.util.PluginLogger;
import net.kyori.adventure.text.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class FabricTextSerializer {
    private static final Gson GSON = new Gson();

    private final MinecraftServer server;
    private final PluginLogger logger;

    public FabricTextSerializer(MinecraftServer server, PluginLogger logger) {
        this.server = server;
        this.logger = logger;
    }

    public void sendLegacyMessage(ServerPlayer player, String message) {
        player.sendSystemMessage(parseLegacyText(message), false);
    }

    public net.minecraft.network.chat.Component parseLegacyText(String message) {
        String normalizedMessage = message == null ? "" : message;
        try {
            String json = AdventureSerializer.toJson(CirrusChatElement.ofLegacyText(normalizedMessage).asComponent());
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            net.minecraft.network.chat.Component nativeComponent = ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(json))
                    .result().orElse(null);
            if (nativeComponent != null) {
                return nativeComponent;
            }
        } catch (Exception e) {
            logger.debug("Failed to parse legacy text: " + e.getMessage());
        }
        return net.minecraft.network.chat.Component.literal(stripAmpersandAndSection(normalizedMessage));
    }

    public void sendInterceptMessage(ServerPlayer player, String message) {
        try {
            String json = AdventureSerializer.toJson(CirrusChatElement.ofLegacyText(message).asComponent());
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            net.minecraft.network.chat.Component component =
                    ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(null);
            if (component != null) {
                player.sendSystemMessage(component, false);
                return;
            }
        } catch (Exception e) {
            logger.debug("Failed to serialize intercept message: " + e.getMessage());
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(stripSection(message)), false);
    }

    public void sendJsonToPlayer(ServerPlayer player, String jsonMessage) {
        try {
            JsonElement element = JsonParser.parseString(jsonMessage);
            fixLegacyHoverEvents(element);
            String fixedJson = GSON.toJson(element);
            Component adventureComponent = AdventureSerializer.serializer().fromJson(fixedJson);
            String normalizedJson = AdventureSerializer.toJson(adventureComponent);
            JsonElement jsonElement = JsonParser.parseString(normalizedJson);
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            net.minecraft.network.chat.Component nativeComponent =
                    ComponentSerialization.CODEC.parse(ops, jsonElement).result().orElse(null);
            if (nativeComponent != null) {
                player.sendSystemMessage(nativeComponent, false);
                return;
            }
        } catch (Exception e) {
            logger.warning("Failed to parse JSON message: " + e.getMessage());
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(stripAmpersandAndSection(jsonMessage)), false);
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
