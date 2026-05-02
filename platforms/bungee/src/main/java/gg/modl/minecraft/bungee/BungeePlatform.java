package gg.modl.minecraft.bungee;

import dev.simplix.cirrus.bungee.wrapper.BungeePlayerWrapper;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.Platform;
import net.md_5.bungee.api.plugin.Plugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.bungee.BungeeLamp;
import revxrsal.commands.bungee.actor.BungeeCommandActor;
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
import lombok.Setter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import java.util.function.Consumer;
import java.io.File;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import gg.modl.minecraft.core.util.PluginLogger;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.chat.ComponentSerializer;

public class BungeePlatform implements Platform {
    private final Plugin plugin;
    private final Logger logger;
    private final File dataFolder;
    private final String configServerName;
    private final PluginLogger pluginLogger;
    private @Setter Cache cache;
    private @Setter LocaleManager localeManager;
    private @Setter StaffModeService staffModeService;
    private @Setter BridgeService bridgeService;
    private @Setter Staff2faService staff2faService;
    private @Setter ChatInputManager chatInputManager;
    private @Setter ReplayService replayService;

    private static volatile boolean skinMethodsResolved = false;
    private static volatile Method getLoginProfileMethod;
    private static volatile Method getPropertiesMethod;
    private static volatile Method getNameMethod;
    private static volatile Method getValueMethod;

    public BungeePlatform(Plugin plugin, Logger logger, File dataFolder, String configServerName) {
        this.plugin = plugin;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.configServerName = configServerName;
        this.pluginLogger = PluginLogger.fromJul(logger);
    }

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
    public void connectToServer(UUID player, String serverName) {
        ProxiedPlayer pp = ProxyServer.getInstance().getPlayer(player);
        if (pp != null) {
            ServerInfo server = ProxyServer.getInstance().getServerInfo(serverName);
            if (server != null) pp.connect(server);
        }
    }

    @Override
    public void staffJsonBroadcast(String jsonMessage) {
        ProxyServer.getInstance().getPlayers().stream()
            .filter(player -> PermissionUtil.isStaff(player.getUniqueId(), cache))
            .filter(player -> staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(player.getUniqueId()))
            .forEach(player -> player.sendMessage(ComponentSerializer.parse(jsonMessage)));
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null && player.isConnected()) player.sendMessage(StringUtil.unescapeNewlines(message));
    }

    @Override
    public void sendJsonMessage(UUID uuid, String jsonMessage) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null && player.isConnected()) player.sendMessage(ComponentSerializer.parse(jsonMessage));
    }

    @Override
    public boolean isOnline(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        return player != null && player.isConnected();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Lamp<BungeeCommandActor> buildLamp(Consumer<Lamp.Builder<? extends CommandActor>> configurator) {
        Lamp.Builder<BungeeCommandActor> builder = BungeeLamp.builder(plugin);
        configurator.accept((Lamp.Builder) builder);
        return builder.build();
    }

    @Override
    public AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        return player != null ? toAbstractPlayer(player) : null;
    }

    @Override
    public AbstractPlayer getAbstractPlayer(String username, boolean queryMojang) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(username);
        return player != null ? toAbstractPlayer(player) : null;
    }

    @Override
    public CirrusPlayerWrapper getPlayerWrapper(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        return player != null ? new BungeePlayerWrapper(player) : null;
    }

    @Override
    public Collection<AbstractPlayer> getOnlinePlayers() {
        return ProxyServer.getInstance().getPlayers().stream()
            .map(this::toAbstractPlayer)
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
    public String getPlatformType() {
        return "bungee";
    }

    @Override
    public void runOnMainThread(Runnable task) {
        task.run();
    }

    @Override
    public void kickPlayer(AbstractPlayer player, String reason) {
        ProxiedPlayer bungeePlayer = ProxyServer.getInstance().getPlayer(player.getUuid());
        if (bungeePlayer != null && bungeePlayer.isConnected()) bungeePlayer.disconnect(new TextComponent(StringUtil.unescapeNewlines(reason)));
    }

    @Override
    public String getServerName() {
        return configServerName;
    }

    @Override
    public String getPlayerServer(UUID uuid) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null && player.getServer() != null) return player.getServer().getInfo().getName();
        return getServerName();
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public DatabaseProvider createLiteBansDatabaseProvider() {
        try {
            if (ProxyServer.getInstance().getPluginManager().getPlugin("LiteBans") == null) return null;
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

    private AbstractPlayer toAbstractPlayer(ProxiedPlayer player) {
        String ip = (player.getSocketAddress() instanceof InetSocketAddress)
                ? ((InetSocketAddress) player.getSocketAddress()).getAddress().getHostAddress() : null;
        return new AbstractPlayer(player.getUniqueId(), player.getName(), ip, player.isConnected());
    }

    @Override
    public void dispatchPlayerCommand(UUID uuid, String command) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null) ProxyServer.getInstance().getPluginManager().dispatchCommand(player, command);
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
            if (!skinMethodsResolved) {
                resolveSkinMethods(player);
            }
            if (getLoginProfileMethod == null) return null;

            Object pendingConnection = player.getPendingConnection();
            Object profile = getLoginProfileMethod.invoke(pendingConnection);
            if (profile == null) return null;
            Object[] properties = (Object[]) getPropertiesMethod.invoke(profile);
            if (properties == null) return null;
            for (Object prop : properties) {
                String name = (String) getNameMethod.invoke(prop);
                if ("textures".equals(name)) return (String) getValueMethod.invoke(prop);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static synchronized void resolveSkinMethods(ProxiedPlayer player) {
        if (skinMethodsResolved) return;
        try {
            Object pendingConnection = player.getPendingConnection();
            getLoginProfileMethod = pendingConnection.getClass().getMethod("getLoginProfile");
            Object profile = getLoginProfileMethod.invoke(pendingConnection);
            if (profile != null) {
                getPropertiesMethod = profile.getClass().getMethod("getProperties");
                Object[] properties = (Object[]) getPropertiesMethod.invoke(profile);
                if (properties != null) {
                    for (Object prop : properties) {
                        getNameMethod = prop.getClass().getMethod("getName");
                        getValueMethod = prop.getClass().getMethod("getValue");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            getLoginProfileMethod = null;
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
