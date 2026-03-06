package gg.modl.minecraft.velocity;

import co.aikar.commands.CommandManager;
import co.aikar.commands.VelocityCommandManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.velocity.wrapper.VelocityPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.database.LiteBansDatabaseProvider;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.StringUtil;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.File;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class VelocityPlatform implements Platform {
    private final ProxyServer server;
    private final VelocityCommandManager commandManager;
    private final Logger logger;
    private final File dataFolder;
    private final String configServerName;
    private @Setter Cache cache;
    private @Setter LocaleManager localeManager;
    private @Setter StaffModeService staffModeService;
    private @Setter BridgeService bridgeService;
    private @Setter Staff2faService staff2faService;

    private static Component colorize(String string) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(string);
    }

    private boolean isAuthenticatedStaff(Player player) {
        return PermissionUtil.isStaff(player.getUniqueId(), cache)
                && (staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()));
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
    public void connectToServer(java.util.UUID playerUuid, String serverName) {
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
        server.getPlayer(uuid).orElseThrow().sendMessage(colorize(StringUtil.unescapeNewlines(message)));
    }

    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        server.getPlayer(uuid).ifPresent(player -> sendJsonToPlayer(player, jsonMessage));
    }

    private void sendJsonToPlayer(Player player, String jsonMessage) {
        try {
            Component component = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(jsonMessage);
            player.sendMessage(component);
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(Component.text("Notification: " + jsonMessage));
        }
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayer(uuid).isPresent();
    }

    @Override
    public CommandManager<?, ?, ?, ?, ?, ?> getCommandManager() {
        return commandManager;
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

        if (player != null) return new AbstractPlayer(player.getUniqueId(), player.getUsername(), true, player.getRemoteAddress().getAddress().getHostAddress());
        if (!queryMojang) return null;

        WebPlayer webPlayer;
        try {
            webPlayer = WebPlayer.getSync(uuid);
        } catch (Exception ignored) {
            return null;
        }

        if (webPlayer == null) return null;
        return new AbstractPlayer(webPlayer.uuid(), webPlayer.name(), false, null);
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        Player player = getOnlinePlayer(username);

        if (player != null) return new AbstractPlayer(player.getUniqueId(), player.getUsername(), true, player.getRemoteAddress().getAddress().getHostAddress());
        if (!queryMojang) return null;

        WebPlayer webPlayer;
        try {
            webPlayer = WebPlayer.getSync(username);
        } catch (Exception ignored) {
            return null;
        }

        if (webPlayer == null) return null;
        return new AbstractPlayer(webPlayer.uuid(), webPlayer.name(), false, null);
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
                        true,
                        player.getRemoteAddress().getAddress().getHostAddress()
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
                true,
                player.getRemoteAddress().getAddress().getHostAddress()
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
    public void runOnMainThread(Runnable task) {
        // velo has no main thread, everything is thread-safe
        task.run();
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        Player velocityPlayer = server.getPlayer(player.getUuid()).orElse(null);
        if (velocityPlayer != null) velocityPlayer.disconnect(colorize(StringUtil.unescapeNewlines(reason)));
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
            if (server.getPluginManager().getPlugin("litebans").isEmpty()) return null;
            Class.forName("litebans.api.Database");
            logger.info("LiteBans plugin detected, using LiteBans API");
            return new LiteBansDatabaseProvider();
        } catch (ClassNotFoundException e) {
            logger.info("LiteBans API not found in classpath");
        } catch (Exception e) {
            logger.warn("Error checking for LiteBans: " + e.getMessage());
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
        for (com.velocitypowered.api.util.GameProfile.Property prop : player.getGameProfileProperties()) {
            if ("textures".equals(prop.getName())) return prop.getValue();
        }
        return null;
    }

    @Override
    public gg.modl.minecraft.core.util.PluginLogger getLogger() {
        return new gg.modl.minecraft.core.util.PluginLogger() {
            @Override public void info(String message) { logger.info(message); }
            @Override public void warning(String message) { logger.warn(message); }
            @Override public void severe(String message) { logger.error(message); }
            @Override public void debug(String message) { logger.debug(message); }
        };
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
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
}
