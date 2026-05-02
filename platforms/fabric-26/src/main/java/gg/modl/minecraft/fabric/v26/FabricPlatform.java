package gg.modl.minecraft.fabric.v26;

import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.simplix.cirrus.text.CirrusChatElement;
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
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class FabricPlatform implements Platform {
    private final MinecraftServer server;
    private final Path dataFolder;
    private final PluginLogger logger;
    private Cache cache;
    private LocaleManager localeManager;
    private StaffModeService staffModeService;
    private BridgeService bridgeService;
    private Staff2faService staff2faService;
    private ChatInputManager chatInputManager;
    private ReplayService replayService;
    private String serverName = "fabric-server";

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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendLegacyMessage(player, string);
        }
    }

    @Override
    public void staffBroadcast(String string) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAuthenticatedStaff(player.getUUID())) {
                sendLegacyMessage(player, string);
            }
        }
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAuthenticatedStaff(player.getUUID())) {
                sendJsonToPlayer(player, jsonMessage);
            }
        }
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            sendLegacyMessage(player, StringUtil.unescapeNewlines(message));
        }
    }

    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            sendJsonToPlayer(player, jsonMessage);
        }
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid) != null;
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
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return new AbstractPlayer(player.getUUID(), player.getName().getString(), player.getIpAddress(), true);
        }
        if (!queryMojang) {
            return null;
        }

        try {
            WebPlayer webPlayer = WebPlayer.getSync(uuid);
            if (webPlayer == null) {
                return null;
            }
            return new AbstractPlayer(webPlayer.getUuid(), webPlayer.getName(), null, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        ServerPlayer player = server.getPlayerList().getPlayer(username);
        if (player != null) {
            return new AbstractPlayer(player.getUUID(), player.getName().getString(), player.getIpAddress(), true);
        }
        if (!queryMojang) {
            return null;
        }

        try {
            WebPlayer webPlayer = WebPlayer.getSync(username);
            if (webPlayer == null) {
                return null;
            }
            return new AbstractPlayer(webPlayer.getUuid(), webPlayer.getName(), null, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return null;
        }
        try {
            Class<?> wrapperClass = Class.forName("dev.simplix.cirrus.fabric.wrapper.FabricPlayerWrapper");
            return (CirrusPlayerWrapper) wrapperClass.getConstructor(ServerPlayer.class).newInstance(player);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return server.getPlayerList().getPlayers().stream()
                .map(player -> new AbstractPlayer(
                        player.getUUID(),
                        player.getName().getString(),
                        player.getIpAddress(),
                        true
                ))
                .collect(Collectors.toList());
    }

    @Override
    public AbstractPlayer getPlayer(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return null;
        }
        return new AbstractPlayer(player.getUUID(), player.getName().getString(), player.getIpAddress(), true);
    }

    @Override
    public int getMaxPlayers() {
        return server.getPlayerList().getMaxPlayers();
    }

    @Override
    public String getServerVersion() {
        return server.getServerVersion();
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
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player.getUuid());
        if (serverPlayer != null) {
            serverPlayer.connection.disconnect(parseLegacyText(StringUtil.unescapeNewlines(reason)));
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
        CommandSourceStack source = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, command);
    }

    @Override
    public String getPlayerSkinTexture(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return null;
        }
        GameProfile profile = player.getGameProfile();
        Property property = profile.properties()
                .get("textures")
                .stream()
                .findFirst()
                .orElse(null);
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

    @Override
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    @Override
    public void setLocaleManager(LocaleManager localeManager) {
        this.localeManager = localeManager;
    }

    @Override
    public void setStaffModeService(StaffModeService staffModeService) {
        this.staffModeService = staffModeService;
    }

    @Override
    public void setBridgeService(BridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    @Override
    public void setStaff2faService(Staff2faService staff2faService) {
        this.staff2faService = staff2faService;
    }

    @Override
    public void setChatInputManager(ChatInputManager chatInputManager) {
        this.chatInputManager = chatInputManager;
    }

    @Override
    public void setReplayService(ReplayService replayService) {
        this.replayService = replayService;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    private boolean isAuthenticatedStaff(UUID uuid) {
        return PermissionUtil.isStaff(uuid, cache)
                && (staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(uuid));
    }

    private void sendJsonToPlayer(ServerPlayer player, String jsonMessage) {
        try {
            JsonElement element = JsonParser.parseString(jsonMessage);
            fixLegacyHoverEvents(element);
            String fixedJson = new Gson().toJson(element);
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
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(stripLegacyFormatting(jsonMessage)), false);
    }

    private void sendLegacyMessage(ServerPlayer player, String message) {
        player.sendSystemMessage(parseLegacyText(message), false);
    }

    private net.minecraft.network.chat.Component parseLegacyText(String message) {
        String normalizedMessage = message == null ? "" : message;
        try {
            String json = AdventureSerializer.toJson(CirrusChatElement.ofLegacyText(normalizedMessage).asComponent());
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            net.minecraft.network.chat.Component nativeComponent = ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(json))
                    .result().orElse(null);
            if (nativeComponent != null) {
                return nativeComponent;
            }
        } catch (Exception ignored) {
        }
        return net.minecraft.network.chat.Component.literal(stripLegacyFormatting(normalizedMessage));
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

    private String stripLegacyFormatting(String message) {
        return (message == null ? "" : message).replaceAll("(?i)[&\u00a7][0-9a-fk-or]", "");
    }
}
