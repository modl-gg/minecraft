package gg.modl.minecraft.fabric.v1_21_1;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.StringUtil;
import gg.modl.minecraft.core.util.WebPlayer;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.fabric.FabricLamp;
import revxrsal.commands.fabric.actor.FabricCommandActor;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FabricPlatform implements Platform {
    private final MinecraftServer server;
    private final Path dataFolder;
    private final PluginLogger logger;
    private @Setter Cache cache;
    private @Setter LocaleManager localeManager;
    private @Setter StaffModeService staffModeService;
    private @Setter BridgeService bridgeService;
    private @Setter Staff2faService staff2faService;
    private @Setter ChatInputManager chatInputManager;
    private @Setter ReplayService replayService;
    private @Setter String serverName = "fabric-server";

    public FabricPlatform(MinecraftServer server, Path dataFolder, PluginLogger logger) {
        this.server = server;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    @Override
    public PluginLogger getLogger() {
        return logger;
    }

    @Override
    public void broadcast(String string) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal(string), false);
        }
    }

    @Override
    public void staffBroadcast(String string) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (isAuthenticatedStaff(player.getUuid())) {
                player.sendMessage(Text.literal(string), false);
            }
        }
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (isAuthenticatedStaff(player.getUuid())) {
                sendJsonToPlayer(player, jsonMessage);
            }
        }
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            player.sendMessage(Text.literal(StringUtil.unescapeNewlines(message)), false);
        }
    }

    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            sendJsonToPlayer(player, jsonMessage);
        }
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayerManager().getPlayer(uuid) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Lamp<FabricCommandActor> buildLamp(Consumer<Lamp.Builder<? extends CommandActor>> configurator) {
        Lamp.Builder<FabricCommandActor> builder = FabricLamp.builder();
        configurator.accept((Lamp.Builder) builder);
        return builder.build();
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            return new AbstractPlayer(player.getUuid(), player.getName().getString(),
                    player.getIp(), true);
        }
        if (!queryMojang) return null;

        WebPlayer webPlayer;
        try {
            webPlayer = WebPlayer.getSync(uuid);
        } catch (Exception ignored) {
            return null;
        }
        if (webPlayer == null) return null;
        return new AbstractPlayer(webPlayer.getUuid(), webPlayer.getName(), null, false);
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(username);
        if (player != null) {
            return new AbstractPlayer(player.getUuid(), player.getName().getString(),
                    player.getIp(), true);
        }
        if (!queryMojang) return null;

        WebPlayer webPlayer;
        try {
            webPlayer = WebPlayer.getSync(username);
        } catch (Exception ignored) {
            return null;
        }
        if (webPlayer == null) return null;
        return new AbstractPlayer(webPlayer.getUuid(), webPlayer.getName(), null, false);
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return null;
        try {
            Class<?> wrapperClass = Class.forName("dev.simplix.cirrus.fabric.wrapper.FabricPlayerWrapper");
            return (CirrusPlayerWrapper) wrapperClass.getConstructor(ServerPlayerEntity.class).newInstance(player);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return server.getPlayerManager().getPlayerList().stream()
                .map(player -> new AbstractPlayer(
                        player.getUuid(),
                        player.getName().getString(),
                        player.getIp(),
                        true
                ))
                .collect(Collectors.toList());
    }

    @Override
    public AbstractPlayer getPlayer(UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return null;
        return new AbstractPlayer(
                player.getUuid(),
                player.getName().getString(),
                player.getIp(),
                true
        );
    }

    @Override
    public int getMaxPlayers() {
        return server.getMaxPlayerCount();
    }

    @Override
    public String getServerVersion() {
        return server.getVersion();
    }

    @Override
    public String getPlatformType() {
        return "FABRIC";
    }

    @Override
    public void runOnMainThread(Runnable task) {
        server.execute(task);
    }

    @Override
    public void runOnGameThread(Runnable task) {
        server.execute(task);
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        ServerPlayerEntity serverPlayer = server.getPlayerManager().getPlayer(player.getUuid());
        if (serverPlayer != null) {
            serverPlayer.networkHandler.disconnect(Text.literal(StringUtil.unescapeNewlines(reason)));
        }
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public File getDataFolder() {
        return dataFolder.toFile();
    }

    @Override
    public DatabaseProvider createLiteBansDatabaseProvider() {
        return null;
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
    }

    @Override
    public String getPlayerSkinTexture(UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return null;
        com.mojang.authlib.GameProfile profile = player.getGameProfile();
        com.mojang.authlib.properties.Property property = profile.getProperties().get("textures")
                .stream().findFirst().orElse(null);
        return property != null ? property.value() : null;
    }

    @Override
    public Cache getCache() {
        return cache;
    }

    @Override
    public LocaleManager getLocaleManager() {
        return localeManager;
    }

    @Override
    public StaffModeService getStaffModeService() {
        return staffModeService;
    }

    @Override
    public BridgeService getBridgeService() {
        return bridgeService;
    }

    @Override
    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    @Override
    public ReplayService getReplayService() {
        return replayService;
    }

    private boolean isAuthenticatedStaff(UUID uuid) {
        return PermissionUtil.isStaff(uuid, cache)
                && (staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(uuid));
    }

    private void sendJsonToPlayer(ServerPlayerEntity player, String jsonMessage) {
        try {
            JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
            fixLegacyHoverEvents(obj);
            String fixedJson = new Gson().toJson(obj);
            Component component = AdventureSerializer.serializer().fromJson(fixedJson);
            String normalizedJson = AdventureSerializer.toJson(component);
            Text text = Text.Serialization.fromJson(normalizedJson, player.getRegistryManager());
            if (text != null) {
                player.sendMessage(text, false);
                return;
            }
        } catch (Exception e) {
            logger.warning("Failed to parse JSON message: " + e.getMessage());
        }
        player.sendMessage(Text.literal(jsonMessage.replaceAll("\u00a7[0-9a-fk-or]", "")), false);
    }

    private void fixLegacyHoverEvents(JsonObject obj) {
        if (obj.has("hoverEvent")) {
            JsonObject hover = obj.getAsJsonObject("hoverEvent");
            if (hover != null && hover.has("value") && !hover.has("contents")) {
                com.google.gson.JsonElement value = hover.remove("value");
                if (value.isJsonPrimitive()) {
                    JsonObject contents = new JsonObject();
                    contents.add("text", value);
                    hover.add("contents", contents);
                } else {
                    hover.add("contents", value);
                }
            }
        }
        if (obj.has("extra") && obj.get("extra").isJsonArray()) {
            for (com.google.gson.JsonElement el : obj.getAsJsonArray("extra")) {
                if (el.isJsonObject()) fixLegacyHoverEvents(el.getAsJsonObject());
            }
        }
    }
}
