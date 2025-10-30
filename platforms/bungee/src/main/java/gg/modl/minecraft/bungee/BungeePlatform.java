package gg.modl.minecraft.bungee;

import co.aikar.commands.BungeeCommandManager;
import co.aikar.commands.CommandManager;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.core.service.database.LiteBansDatabaseProvider;
import lombok.RequiredArgsConstructor;
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

    @Override
    public void broadcast(String string) {
        TextComponent message = new TextComponent(ChatColor.translateAlternateColorCodes('&', string));
        ProxyServer.getInstance().broadcast(message);
    }

    @Override
    public void staffBroadcast(String string) {
        TextComponent message = new TextComponent(ChatColor.translateAlternateColorCodes('&', string));
        ProxyServer.getInstance().getPlayers().stream()
            .filter(player -> player.hasPermission("modl.staff"))
            .forEach(player -> player.sendMessage(message));
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
            // Handle both escaped newlines and literal \n sequences
            String formattedMessage = message.replace("\\n", "\n").replace("\\\\n", "\n");
            player.sendMessage(formattedMessage);
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
        return null;
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
        ProxyServer.getInstance().getScheduler().schedule(
            ProxyServer.getInstance().getPluginManager().getPlugin("modl"),
            task,
            0, TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        ProxiedPlayer bungeePlayer = ProxyServer.getInstance().getPlayer(player.getUuid());
        if (bungeePlayer != null && bungeePlayer.isConnected()) {
            // Handle both escaped newlines and literal \n sequences
            String formattedReason = reason.replace("\\n", "\n").replace("\\\\n", "\n");
            bungeePlayer.disconnect(new TextComponent(formattedReason));
        }
    }

    @Override
    public String getServerName() {
        return "bungee-proxy"; // Default server name, can be made configurable
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
}
