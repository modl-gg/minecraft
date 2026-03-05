package gg.modl.minecraft.bungee;

import co.aikar.commands.BungeeCommandManager;
import co.aikar.commands.CommandManager;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.StringUtil;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.bungee.wrapper.BungeePlayerWrapper;
import gg.modl.minecraft.core.service.database.LiteBansDatabaseProvider;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class BungeePlatform implements Platform {
    private final BungeeCommandManager commandManager;
    private final Logger logger;
    private final File dataFolder;
    private final String configServerName;
    @Setter
    private Cache cache;
    @Setter
    private LocaleManager localeManager;
    @Setter
    private StaffModeService staffModeService;
    @Setter
    private gg.modl.minecraft.core.service.BridgeService bridgeService;
    @Setter
    private Staff2faService staff2faService;

    @Override
    public void broadcast(String string) {
        TextComponent message = new TextComponent(ChatColor.translateAlternateColorCodes('&', string));
        ProxyServer.getInstance().broadcast(message);
    }

    @Override
    public void staffBroadcast(String string) {
        TextComponent message = new TextComponent(ChatColor.translateAlternateColorCodes('&', string));
        ProxyServer.getInstance().getPlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .filter(player -> staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()))
            .forEach(player -> player.sendMessage(message));
    }

    @Override
    public void staffChatBroadcast(String message) {
        String msg = ChatColor.translateAlternateColorCodes('&', message);
        ProxyServer.getInstance().getPlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .filter(player -> staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()))
            .filter(player -> cache.isStaffNotificationsEnabled(player.getUniqueId()))
            .forEach(player -> player.sendMessage(new TextComponent(msg)));
    }

    @Override
    public void connectToServer(java.util.UUID player, String serverName) {
        ProxiedPlayer pp = ProxyServer.getInstance().getPlayer(player);
        if (pp != null) {
            net.md_5.bungee.api.config.ServerInfo server = ProxyServer.getInstance().getServerInfo(serverName);
            if (server != null) {
                pp.connect(server);
            }
        }
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        ProxyServer.getInstance().getPlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .filter(player -> staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()))
            .forEach(player -> player.sendMessage(net.md_5.bungee.chat.ComponentSerializer.parse(jsonMessage)));
    }

    @Override
    public void disconnect(UUID uuid, String message) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null && player.isConnected()) {
            player.disconnect(new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
        }
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null && player.isConnected()) {
            player.sendMessage(StringUtil.unescapeNewlines(message));
        }
    }
    
    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null && player.isConnected()) {
            // Parse and send JSON message using BungeeCord's ComponentSerializer
            player.sendMessage(net.md_5.bungee.chat.ComponentSerializer.parse(jsonMessage));
        }
    }

    @Override
    public boolean isOnline(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        return player != null && player.isConnected();
    }

    @NotNull
    @Override
    public CommandManager<?, ?, ?, ?, ?, ?> getCommandManager() {
        return commandManager;
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null) {
            String ipAddress = null;
            if (player.getSocketAddress() instanceof InetSocketAddress) {
                ipAddress = ((InetSocketAddress) player.getSocketAddress()).getAddress().getHostAddress();
            }
            return new AbstractPlayer(uuid, player.getName(), player.isConnected(), ipAddress);
        }
        
        // BungeeCord doesn't have offline player support like Bukkit
        return null;
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(username);
        if (player != null) {
            String ipAddress = null;
            if (player.getSocketAddress() instanceof InetSocketAddress) {
                ipAddress = ((InetSocketAddress) player.getSocketAddress()).getAddress().getHostAddress();
            }
            return new AbstractPlayer(player.getUniqueId(), player.getName(), player.isConnected(), ipAddress);
        }
        
        // BungeeCord doesn't have offline player support like Bukkit
        return null;
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        return player != null ? new BungeePlayerWrapper(player) : null;
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return ProxyServer.getInstance().getPlayers().stream()
            .map(player -> {
                String ipAddress = null;
                if (player.getSocketAddress() instanceof InetSocketAddress) {
                    ipAddress = ((InetSocketAddress) player.getSocketAddress()).getAddress().getHostAddress();
                }
                return new AbstractPlayer(player.getUniqueId(), player.getName(), player.isConnected(), ipAddress);
            })
            .collect(Collectors.toList());
    }

    @Override
    public AbstractPlayer getPlayer(UUID uuid) {
        return getAbstractPlayer(uuid, false);
    }

    @Override
    public int getMaxPlayers() {
        return ProxyServer.getInstance().getConfig().getPlayerLimit();
    }

    @Override
    public String getServerVersion() {
        return ProxyServer.getInstance().getVersion();
    }

    @Override
    public void runOnMainThread(Runnable task) {
        // Execute directly — menus (Cirrus/PacketEvents) and messages are thread-safe.
        // BungeeCord has no main thread concept; all APIs are safe from any thread.
        task.run();
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        ProxiedPlayer bungeePlayer = ProxyServer.getInstance().getPlayer(player.getUuid());
        if (bungeePlayer != null && bungeePlayer.isConnected()) {
            bungeePlayer.disconnect(new TextComponent(StringUtil.unescapeNewlines(reason)));
        }
    }

    @Override
    public String getServerName() {
        return configServerName;
    }

    @Override
    public String getPlayerServer(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null && player.getServer() != null) {
            return player.getServer().getInfo().getName();
        }
        return getServerName();
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public DatabaseProvider createLiteBansDatabaseProvider() {
        try {
            // Check if LiteBans plugin is loaded
            if (ProxyServer.getInstance().getPluginManager().getPlugin("LiteBans") != null) {
                // Verify LiteBans API is accessible
                Class.forName("litebans.api.Database");
                logger.info("[Migration] LiteBans plugin detected, using LiteBans API");
                return new LiteBansDatabaseProvider();
            }
        } catch (ClassNotFoundException e) {
            logger.info("[Migration] LiteBans API not found in classpath");
        } catch (Exception e) {
            logger.warning("[Migration] Error checking for LiteBans: " + e.getMessage());
        }

        return null;
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public void dispatchPlayerCommand(UUID uuid, String command) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null) {
            ProxyServer.getInstance().getPluginManager().dispatchCommand(player, command);
        }
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        ProxyServer.getInstance().getPluginManager().dispatchCommand(
                ProxyServer.getInstance().getConsole(), command);
    }

    @Override
    public String getPlayerSkinTexture(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player == null) return null;
        try {
            // InitialHandler.getLoginProfile() is an internal BungeeCord class - use reflection
            Object pendingConnection = player.getPendingConnection();
            Object profile = pendingConnection.getClass().getMethod("getLoginProfile").invoke(pendingConnection);
            if (profile == null) return null;
            Object[] properties = (Object[]) profile.getClass().getMethod("getProperties").invoke(profile);
            if (properties == null) return null;
            for (Object prop : properties) {
                String name = (String) prop.getClass().getMethod("getName").invoke(prop);
                if ("textures".equals(name)) {
                    return (String) prop.getClass().getMethod("getValue").invoke(prop);
                }
            }
        } catch (Exception e) {
            // Reflection failure - return null
        }
        return null;
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
    public gg.modl.minecraft.core.service.BridgeService getBridgeService() {
        return bridgeService;
    }
}
