package gg.modl.minecraft.spigot;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.spigot.wrapper.SpigotPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.BukkitLampConfig;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.database.LiteBansDatabaseProvider;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.StringUtil;
import gg.modl.minecraft.spigot.bridge.folia.FoliaSchedulerHelper;
import lombok.Setter;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.function.Consumer;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import gg.modl.minecraft.core.util.PluginLogger;

public class SpigotPlatform implements Platform {
    private final Logger logger;
    private final File dataFolder;
    private final String configServerName;
    private final JavaPlugin plugin;
    private final boolean lateBootstrap;
    private final PluginLogger pluginLogger;
    private @Setter Cache cache;
    private @Setter LocaleManager localeManager;
    private @Setter StaffModeService staffModeService;
    private @Setter BridgeService bridgeService;
    private @Setter Staff2faService staff2faService;
    private @Setter ChatInputManager chatInputManager;
    private @Setter ReplayService replayService;

    private static volatile boolean skinMethodsResolved = false;
    private static volatile Method getPlayerProfileMethod;
    private static volatile Method getPropertiesMethod;
    private static volatile Method getNameMethod;
    private static volatile Method getValueMethod;

    public SpigotPlatform(JavaPlugin plugin, Logger logger, File dataFolder, String configServerName) {
        this(plugin, logger, dataFolder, configServerName, false);
    }

    public SpigotPlatform(JavaPlugin plugin, Logger logger, File dataFolder, String configServerName, boolean lateBootstrap) {
        this.plugin = plugin;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.configServerName = configServerName;
        this.lateBootstrap = lateBootstrap;
        this.pluginLogger = PluginLogger.fromJul(logger);
    }

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
    public void staffJsonBroadcast(String jsonMessage) {
        Bukkit.getOnlinePlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .filter(player -> staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()))
            .forEach(player -> player.spigot().sendMessage(ComponentSerializer.parse(jsonMessage)));
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

    @Override
    @SuppressWarnings("unchecked")
    public Lamp<BukkitCommandActor> buildLamp(Consumer<Lamp.Builder<? extends CommandActor>> configurator) {
        Lamp.Builder<BukkitCommandActor> builder = BukkitLamp.builder(createLampConfig());
        configurator.accept((Lamp.Builder) builder);
        return builder.build();
    }

    BukkitLampConfig<BukkitCommandActor> createLampConfig() {
        return BukkitLampConfig.<BukkitCommandActor>builder(plugin)
            .disableBrigadier(shouldDisableBrigadier())
            .build();
    }

    boolean shouldDisableBrigadier() {
        return lateBootstrap;
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return toAbstractPlayer(player);

        if (!queryMojang) return null;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return new AbstractPlayer(uuid, offlinePlayer.getName(), null, false);
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        Player player = Bukkit.getPlayer(username);
        if (player != null) return toAbstractPlayer(player);

        if (!queryMojang) return null;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        if (offlinePlayer.hasPlayedBefore()) return new AbstractPlayer(offlinePlayer.getUniqueId(), offlinePlayer.getName(), null, false);
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
    public String getPlatformType() {
        return "spigot";
    }

    @Override
    public void runOnMainThread(Runnable task) {
        if (FoliaSchedulerHelper.isFolia()) {
            FoliaSchedulerHelper.runGlobal(plugin, task);
            return;
        }
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
            return new LiteBansDatabaseProvider();
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            logger.warning("Error checking for LiteBans: " + e.getMessage());
        }
        return null;
    }

    @Override
    public PluginLogger getLogger() {
        return pluginLogger;
    }

    private AbstractPlayer toAbstractPlayer(Player player) {
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        return new AbstractPlayer(player.getUniqueId(), player.getName(), ip, player.isOnline());
    }

    @Override
    public void dispatchPlayerCommand(UUID uuid, String command) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        if (FoliaSchedulerHelper.isFolia()) {
            FoliaSchedulerHelper.runForEntity(plugin, player, () -> player.performCommand(command));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
        }
    }

    @Override
    public String getPlayerSkinTexture(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return null;
        try {
            if (!skinMethodsResolved) {
                resolveSkinMethods(player);
            }
            if (getPlayerProfileMethod == null) return null;

            Object profile = getPlayerProfileMethod.invoke(player);
            Collection<?> properties = (Collection<?>) getPropertiesMethod.invoke(profile);
            for (Object prop : properties) {
                String name = (String) getNameMethod.invoke(prop);
                if ("textures".equals(name)) return (String) getValueMethod.invoke(prop);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static synchronized void resolveSkinMethods(Player player) {
        if (skinMethodsResolved) return;
        try {
            getPlayerProfileMethod = player.getClass().getMethod("getPlayerProfile");
            Object profile = getPlayerProfileMethod.invoke(player);
            getPropertiesMethod = profile.getClass().getMethod("getProperties");
            Collection<?> properties = (Collection<?>) getPropertiesMethod.invoke(profile);
            for (Object prop : properties) {
                getNameMethod = prop.getClass().getMethod("getName");
                getValueMethod = prop.getClass().getMethod("getValue");
                break;
            }
        } catch (Exception e) {
            getPlayerProfileMethod = null;
        }
        skinMethodsResolved = true;
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

    @Override
    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    @Override
    public ReplayService getReplayService() {
        return replayService;
    }
}
