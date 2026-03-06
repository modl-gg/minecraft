package gg.modl.minecraft.spigot;

import co.aikar.commands.BukkitCommandManager;
import co.aikar.commands.CommandManager;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.spigot.wrapper.SpigotPlayerWrapper;
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
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

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
    private final JavaPlugin plugin;
    @Setter private Cache cache;
    @Setter private LocaleManager localeManager;
    @Setter private StaffModeService staffModeService;
    @Setter private BridgeService bridgeService;
    @Setter private Staff2faService staff2faService;

    @Override
    public void broadcast(String string) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', string));
    }

    @Override
    public void staffBroadcast(String string) {
        String message = ChatColor.translateAlternateColorCodes('&', string);
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .filter(player -> staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()))
            .forEach(player -> player.sendMessage(message));
    }

    @Override
    public void staffChatBroadcast(String message) {
        String msg = ChatColor.translateAlternateColorCodes('&', message);
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .filter(player -> staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()))
            .filter(player -> cache.isStaffNotificationsEnabled(player.getUniqueId()))
            .forEach(player -> player.sendMessage(msg));
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .filter(player -> staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()))
            .forEach(player -> player.spigot().sendMessage(ComponentSerializer.parse(jsonMessage)));
    }

    @Override
    public void disconnect(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) player.kickPlayer(ChatColor.translateAlternateColorCodes('&', message));
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) player.sendMessage(StringUtil.unescapeNewlines(message));
    }
    
    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) player.spigot().sendMessage(ComponentSerializer.parse(jsonMessage));
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    @NotNull @Override
    public CommandManager<?, ?, ?, ?, ?, ?> getCommandManager() {
        return commandManager;
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return toAbstractPlayer(player);

        if (!queryMojang) return null;
        var offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return new AbstractPlayer(uuid, offlinePlayer.getName(), false, null);
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        Player player = Bukkit.getPlayer(username);
        if (player != null) return toAbstractPlayer(player);

        if (!queryMojang) return null;
        var offlinePlayer = Bukkit.getOfflinePlayer(username);
        if (offlinePlayer.hasPlayedBefore()) return new AbstractPlayer(offlinePlayer.getUniqueId(), offlinePlayer.getName(), false, null);
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
            .map(this::toAbstractPlayer)
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
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runOnGameThread(Runnable task) {
        runOnMainThread(task);
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        Player bukkitPlayer = Bukkit.getPlayer(player.getUuid());
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) bukkitPlayer.kickPlayer(StringUtil.unescapeNewlines(reason));
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
            if (Bukkit.getPluginManager().getPlugin("LiteBans") == null) return null;
            Class.forName("litebans.api.Database");
            logger.info("LiteBans plugin detected, using LiteBans API");
            return new LiteBansDatabaseProvider();
        } catch (ClassNotFoundException e) {
            logger.info("LiteBans API not found in classpath");
        } catch (Exception e) {
            logger.warning("Error checking for LiteBans: " + e.getMessage());
        }
        return null;
    }

    public Logger getLogger() {
        return logger;
    }

    private AbstractPlayer toAbstractPlayer(Player player) {
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        return new AbstractPlayer(player.getUniqueId(), player.getName(), player.isOnline(), ip);
    }

    @Override
    public void dispatchPlayerCommand(UUID uuid, String command) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
    }

    @Override
    public String getPlayerSkinTexture(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return null;
        try {
            // Reflection required: getPlayerProfile() only available on Spigot 1.18.1+
            Object profile = player.getClass().getMethod("getPlayerProfile").invoke(player);
            java.util.Collection<?> properties = (java.util.Collection<?>) profile.getClass().getMethod("getProperties").invoke(profile);
            for (Object prop : properties) {
                String name = (String) prop.getClass().getMethod("getName").invoke(prop);
                if ("textures".equals(name)) return (String) prop.getClass().getMethod("getValue").invoke(prop);
            }
        } catch (Exception ignored) {}
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
    public BridgeService getBridgeService() {
        return bridgeService;
    }
}
