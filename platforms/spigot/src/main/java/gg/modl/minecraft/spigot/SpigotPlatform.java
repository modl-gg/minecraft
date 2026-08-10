package gg.modl.minecraft.spigot;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.spigot.wrapper.SpigotPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.StaffAudience;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.BukkitLampConfig;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import gg.modl.minecraft.core.service.database.LiteBansDatabaseProvider;
import gg.modl.minecraft.core.util.StringUtil;
import gg.modl.minecraft.spigot.bridge.folia.FoliaScheduler;
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
import java.util.logging.Level;
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
    private final FoliaScheduler foliaScheduler;
    private @Setter StaffAudience staffAudience;

    private static final String TEXTURES_PROPERTY_NAME = "textures";
    private static final String PLAYER_PROFILE_METHOD_NAME = "getPlayerProfile";
    private static final String PROFILE_PROPERTIES_METHOD_NAME = "getProperties";
    private static final String[] PROPERTY_NAME_ACCESSOR_CANDIDATES = {"getName", "name"};
    private static final String[] PROPERTY_VALUE_ACCESSOR_CANDIDATES = {"getValue", "value"};

    private volatile boolean profileMethodsResolved = false;
    private volatile boolean propertyAccessorsResolved = false;
    private volatile Method getPlayerProfileMethod;
    private volatile Method getPropertiesMethod;
    private volatile Method propertyNameMethod;
    private volatile Method propertyValueMethod;

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
        this.foliaScheduler = new FoliaScheduler(plugin);
    }

    @Override
    public void broadcast(String string) {
        runOnMainThread(() -> Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', string)));
    }

    @Override
    public void staffBroadcast(String string) {
        String message = ChatColor.translateAlternateColorCodes('&', string);
        runOnMainThread(() -> Bukkit.getOnlinePlayers().stream()
            .filter(player -> staffAudience != null && staffAudience.includes(player.getUniqueId()))
            .forEach(player -> player.sendMessage(message)));
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        runOnMainThread(() -> Bukkit.getOnlinePlayers().stream()
            .filter(player -> staffAudience != null && staffAudience.includes(player.getUniqueId()))
            .forEach(player -> player.spigot().sendMessage(ComponentSerializer.parse(jsonMessage))));
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
        if (FoliaScheduler.isFolia()) {
            foliaScheduler.runGlobal(task);
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        if (player == null) return;
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
        if (FoliaScheduler.isFolia()) {
            foliaScheduler.runForEntity(player, () -> player.performCommand(command));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
        }
    }

    @Override
    public String getPlayerSkinTexture(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return null;
        try {
            resolveProfileMethods(player);
            if (getPlayerProfileMethod == null || getPropertiesMethod == null) return null;

            Object profile = getPlayerProfileMethod.invoke(player);
            Collection<?> properties = (Collection<?>) getPropertiesMethod.invoke(profile);
            for (Object property : properties) {
                resolvePropertyAccessors(property);
                if (propertyNameMethod == null || propertyValueMethod == null) return null;

                String name = (String) propertyNameMethod.invoke(property);
                if (TEXTURES_PROPERTY_NAME.equals(name)) return (String) propertyValueMethod.invoke(property);
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            logger.log(Level.FINE, "Failed to read native skin texture for " + uuid, e);
        }
        return null;
    }

    private synchronized void resolveProfileMethods(Player player) {
        if (profileMethodsResolved) return;
        try {
            getPlayerProfileMethod = player.getClass().getMethod(PLAYER_PROFILE_METHOD_NAME);
            Object profile = getPlayerProfileMethod.invoke(player);
            getPropertiesMethod = profile.getClass().getMethod(PROFILE_PROPERTIES_METHOD_NAME);
        } catch (ReflectiveOperationException | ClassCastException e) {
            getPlayerProfileMethod = null;
            getPropertiesMethod = null;
            logger.warning("Native skin texture API unavailable on this server version; skin caching will fall back to Mojang lookups (" + e + ")");
        }
        profileMethodsResolved = true;
    }

    private synchronized void resolvePropertyAccessors(Object property) {
        if (propertyAccessorsResolved) return;
        propertyNameMethod = findAccessor(property.getClass(), PROPERTY_NAME_ACCESSOR_CANDIDATES);
        propertyValueMethod = findAccessor(property.getClass(), PROPERTY_VALUE_ACCESSOR_CANDIDATES);
        if (propertyNameMethod == null || propertyValueMethod == null) {
            propertyNameMethod = null;
            propertyValueMethod = null;
            logger.warning("Unrecognized profile property type " + property.getClass().getName() + "; skin caching will fall back to Mojang lookups");
        }
        propertyAccessorsResolved = true;
    }

    private static Method findAccessor(Class<?> type, String... candidateNames) {
        for (String candidateName : candidateNames) {
            try {
                return type.getMethod(candidateName);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }
}
