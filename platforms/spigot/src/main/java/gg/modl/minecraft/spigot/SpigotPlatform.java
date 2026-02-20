package gg.modl.minecraft.spigot;

import co.aikar.commands.BukkitCommandManager;
import co.aikar.commands.CommandManager;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.StringUtil;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.spigot.wrapper.SpigotPlayerWrapper;
import gg.modl.minecraft.core.service.database.LiteBansDatabaseProvider;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.md_5.bungee.chat.ComponentSerializer;

import java.io.File;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SpigotPlatform implements Platform {
    private final BukkitCommandManager commandManager;
    private final Logger logger;
    private final File dataFolder;
    private final String configServerName;
    @Setter
    private Cache cache;
    @Setter
    private LocaleManager localeManager;

    @Override
    public void broadcast(String string) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', string));
    }

    @Override
    public void staffBroadcast(String string) {
        String message = ChatColor.translateAlternateColorCodes('&', string);
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .forEach(player -> player.sendMessage(message));
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .forEach(player -> player.spigot().sendMessage(ComponentSerializer.parse(jsonMessage)));
    }

    @Override
    public void disconnect(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(StringUtil.unescapeNewlines(message));
        }
    }
    
    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            // Use Spigot's JSON message API
            player.spigot().sendMessage(ComponentSerializer.parse(jsonMessage));
        }
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    @NotNull
    @Override
    public CommandManager<?, ?, ?, ?, ?, ?> getCommandManager() {
        return commandManager;
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return new AbstractPlayer(uuid, player.getName(), player.isOnline(), 
                player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null);
        }
        
        if (queryMojang) {
            // Query offline player from Bukkit
            var offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            return new AbstractPlayer(uuid, offlinePlayer.getName(), false, null);
        }
        
        return null;
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        Player player = Bukkit.getPlayer(username);
        if (player != null) {
            return new AbstractPlayer(player.getUniqueId(), player.getName(), player.isOnline(),
                player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null);
        }
        
        if (queryMojang) {
            // Query offline player from Bukkit
            var offlinePlayer = Bukkit.getOfflinePlayer(username);
            if (offlinePlayer.hasPlayedBefore()) {
                return new AbstractPlayer(offlinePlayer.getUniqueId(), offlinePlayer.getName(), false, null);
            }
        }
        
        return null;
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? new SpigotPlayerWrapper(player) : null;
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream()
            .map(player -> new AbstractPlayer(player.getUniqueId(), player.getName(), true,
                player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null))
            .collect(Collectors.toList());
    }

    @Override
    public AbstractPlayer getPlayer(UUID uuid) {
        return getAbstractPlayer(uuid, false);
    }

    @Override
    public int getMaxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    @Override
    public String getServerVersion() {
        return Bukkit.getVersion();
    }

    @Override
    public void runOnMainThread(Runnable task) {
        // Execute directly — menus (Cirrus/PacketEvents) and messages are thread-safe.
        // Use runOnGameThread() for operations that require the Bukkit main thread.
        task.run();
    }

    @Override
    public void runOnGameThread(Runnable task) {
        // Schedule to Bukkit main thread — required for operations that Paper's
        // AsyncCatcher blocks from async threads (e.g. player.kickPlayer())
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("modl"), task);
        }
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        Player bukkitPlayer = Bukkit.getPlayer(player.getUuid());
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            bukkitPlayer.kickPlayer(StringUtil.unescapeNewlines(reason));
        }
    }

    @Override
    public String getServerName() {
        return configServerName;
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public DatabaseProvider createLiteBansDatabaseProvider() {
        try {
            // Check if LiteBans plugin is loaded
            if (Bukkit.getPluginManager().getPlugin("LiteBans") != null) {
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
}
