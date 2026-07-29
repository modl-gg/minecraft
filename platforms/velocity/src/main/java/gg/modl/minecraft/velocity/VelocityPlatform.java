package gg.modl.minecraft.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.velocity.wrapper.VelocityPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.velocity.VelocityLamp;
import revxrsal.commands.velocity.VelocityVisitors;
import revxrsal.commands.velocity.actor.VelocityCommandActor;
import gg.modl.minecraft.core.StaffAudience;
import gg.modl.minecraft.core.service.database.LiteBansDatabaseProvider;
import gg.modl.minecraft.core.util.StringUtil;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;
import gg.modl.minecraft.core.integration.mojang.WebPlayer;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.io.File;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import com.velocitypowered.api.util.GameProfile;
import gg.modl.minecraft.core.util.PluginLogger;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public class VelocityPlatform implements Platform {
    private final ProxyServer server;
    private final Object plugin;
    private final Logger logger;
    private final File dataFolder;
    private final String configServerName;
    private @Setter StaffAudience staffAudience;

    private final PluginLogger pluginLogger;

    public VelocityPlatform(Object plugin, ProxyServer server, Logger logger, File dataFolder,
                            String configServerName, PluginLogger pluginLogger) {
        this.server = server;
        this.plugin = plugin;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.configServerName = configServerName;
        this.pluginLogger = pluginLogger;
    }

    private static Component colorize(String string) {
        return Colors.legacy(string);
    }

    private boolean isAuthenticatedStaff(Player player) {
        return staffAudience != null && staffAudience.includes(player.getUniqueId());
    }

    @Override
    public void broadcast(String string) {
        server.getAllPlayers().forEach(player -> player.sendMessage(colorize(string)));
    }

    @Override
    public void staffBroadcast(String string) {
        server.getAllPlayers().stream()
            .filter(this::isAuthenticatedStaff)
            .forEach(player -> player.sendMessage(colorize(string)));
    }

    @Override
    public void connectToServer(UUID playerUuid, String serverName) {
        server.getPlayer(playerUuid).ifPresent(player ->
            server.getServer(serverName).ifPresent(srv ->
                player.createConnectionRequest(srv).fireAndForget()));
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        server.getAllPlayers().stream()
            .filter(this::isAuthenticatedStaff)
            .forEach(player -> sendJsonToPlayer(player, jsonMessage));
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        server.getPlayer(uuid).ifPresent(player -> player.sendMessage(colorize(StringUtil.unescapeNewlines(message))));
    }

    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        server.getPlayer(uuid).ifPresent(player -> sendJsonToPlayer(player, jsonMessage));
    }

    private void sendJsonToPlayer(Player player, String jsonMessage) {
        try {
            Component component = GsonComponentSerializer.gson().deserialize(jsonMessage);
            player.sendMessage(component);
        } catch (Exception e) {
            logger.warn("Failed to send JSON message to player", e);
            player.sendMessage(Component.text("Notification: " + jsonMessage));
        }
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayer(uuid).isPresent();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Lamp<VelocityCommandActor> buildLamp(Consumer<Lamp.Builder<? extends CommandActor>> configurator) {
        Lamp.Builder<VelocityCommandActor> builder = VelocityLamp.builder(plugin, server);
        configurator.accept((Lamp.Builder) builder);
        return builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void finalizeLampRegistration(Lamp<? extends CommandActor> lamp) {
        Lamp<VelocityCommandActor> velocityLamp = (Lamp<VelocityCommandActor>) lamp;
        velocityLamp.accept(VelocityVisitors.brigadier(server));
    }

    private Player getOnlinePlayer(String username) {
        return server.getPlayer(username).orElse(null);
    }

    private Player getOnlinePlayer(UUID uuid) {
        return server.getPlayer(uuid).orElse(null);
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        Player player = getOnlinePlayer(uuid);
        if (player != null) return toOnlineAbstractPlayer(player);
        return resolveOfflineAbstractPlayer(queryMojang, () -> MojangProfiles.client().getSync(uuid));
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        Player player = getOnlinePlayer(username);
        if (player != null) return toOnlineAbstractPlayer(player);
        return resolveOfflineAbstractPlayer(queryMojang, () -> MojangProfiles.client().getSync(username));
    }

    private AbstractPlayer toOnlineAbstractPlayer(Player player) {
        return new AbstractPlayer(player.getUniqueId(), player.getUsername(),
                player.getRemoteAddress().getAddress().getHostAddress(), true);
    }

    private AbstractPlayer resolveOfflineAbstractPlayer(boolean queryMojang, Supplier<WebPlayer> lookup) {
        if (!queryMojang) return null;

        WebPlayer webPlayer;
        try {
            webPlayer = lookup.get();
        } catch (Exception ignored) {
            return null;
        }

        if (webPlayer == null) return null;
        return new AbstractPlayer(webPlayer.getUuid(), webPlayer.getName(), null, false);
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
        Player player = getOnlinePlayer(uuid);
        return player != null ? new VelocityPlayerWrapper(player) : null;
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return server.getAllPlayers().stream()
                .map(player -> new AbstractPlayer(
                        player.getUniqueId(),
                        player.getUsername(),
                        player.getRemoteAddress().getAddress().getHostAddress(),
                        true
                ))
                .collect(Collectors.toList());
    }

    @Override
    public AbstractPlayer getPlayer(UUID uuid) {
        Player player = server.getPlayer(uuid).orElse(null);
        if (player == null) return null;

        return new AbstractPlayer(
                player.getUniqueId(),
                player.getUsername(),
                player.getRemoteAddress().getAddress().getHostAddress(),
                true
        );
    }

    @Override
    public int getMaxPlayers() {
        return server.getConfiguration().getShowMaxPlayers();
    }

    @Override
    public String getServerVersion() {
        return server.getVersion().getVersion();
    }

    @Override
    public String getPlatformType() {
        return "velocity";
    }

    @Override
    public void runOnMainThread(Runnable task) {
        task.run();
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        if (player == null) return;
        server.getPlayer(player.getUuid()).ifPresent(velocityPlayer -> velocityPlayer.disconnect(colorize(StringUtil.unescapeNewlines(reason))));
    }

    @Override
    public String getServerName() {
        return configServerName;
    }

    @Override
    public String getPlayerServer(UUID uuid) {
        return server.getPlayer(uuid)
            .flatMap(Player::getCurrentServer)
            .map(conn -> conn.getServerInfo().getName())
            .orElse(getServerName());
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public DatabaseProvider createLiteBansDatabaseProvider() {
        try {
            if (!server.getPluginManager().getPlugin("litebans").isPresent()) return null;
            Class.forName("litebans.api.Database");
            return new LiteBansDatabaseProvider();
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            logger.warn("Error checking for LiteBans: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void dispatchPlayerCommand(UUID uuid, String command) {
        server.getPlayer(uuid).ifPresent(player ->
                server.getCommandManager().executeAsync(player, command));
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    @Override
    public String getPlayerSkinTexture(UUID uuid) {
        Player player = getOnlinePlayer(uuid);
        if (player == null) return null;
        for (GameProfile.Property prop : player.getGameProfileProperties()) {
            if ("textures".equals(prop.getName())) return prop.getValue();
        }
        return null;
    }

    @Override
    public PluginLogger getLogger() {
        return pluginLogger;
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }

}
