package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.chat.ChatService;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.core.login.LoginPipeline;
import gg.modl.minecraft.core.login.LoginService;
import gg.modl.minecraft.core.login.ProxyLoginFlow;
import gg.modl.minecraft.core.session.ServerSwitchService;
import gg.modl.minecraft.core.util.StringUtil;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

@RequiredArgsConstructor
public class BungeeListener implements Listener {
    private final BungeePlatform platform;
    private final Cache cache;
    private final Plugin plugin;
    private final LoginPipeline loginPipeline;
    private final ChatService chatService;
    private final CommandInterceptService commandInterceptService;
    private final ProxyLoginFlow proxyLoginFlow;
    private final ServerSwitchService serverSwitchService;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        event.registerIntent(plugin);
        try {
            String ipAddress = extractIpAddress(event.getConnection().getSocketAddress());
            proxyLoginFlow.begin(event.getConnection().getUniqueId(), event.getConnection().getName(),
                            ipAddress, platform.getServerName())
                    .whenComplete((result, failure) -> applyLoginResult(event, result, failure));
        } catch (Throwable failure) {
            denyLogin(event, loginPipeline.getLoginService().unverifiableBanStatusMessage(), failure);
            event.completeIntent(plugin);
        }
    }

    private void applyLoginResult(LoginEvent event, LoginService.LoginResult result, Throwable failure) {
        try {
            if (failure != null) {
                denyLogin(event, loginPipeline.getLoginService().unverifiableBanStatusMessage(), failure);
                return;
            }
            String message = loginPipeline.getLoginService().denialMessage(result);
            if (message != null) denyLogin(event, message, null);
        } catch (Throwable applyFailure) {
            denyLogin(event, loginPipeline.getLoginService().unverifiableBanStatusMessage(), applyFailure);
        } finally {
            event.completeIntent(plugin);
        }
    }

    private void denyLogin(LoginEvent event, String reason, Throwable failure) {
        event.setCancelReason(platform.toKickComponent(reason));
        event.setCancelled(true);
        if (failure == null) {
            platform.getLogger().warning("Login blocked for " + event.getConnection().getName() + ": " + reason);
        } else {
            platform.getLogger().warning("Login verification failed for " + event.getConnection().getName()
                    + " - denying login", failure);
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        loginPipeline.getPlayerSessionService().handlePlayerJoin(uuid, event.getPlayer().getName());

        String texture = platform.getPlayerSkinTexture(uuid);
        if (texture != null) cache.cacheSkinTexture(uuid, texture);

        LoginCache.CachedLoginResult cachedResult = loginPipeline.getLoginCache().getCachedLoginResult(uuid);
        loginPipeline.getLoginService().cacheLoginData(uuid, cachedResult != null ? cachedResult.getResponse() : null);
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        loginPipeline.getPlayerSessionService().handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        serverSwitchService.handleServerSwitch(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                platform.getPlayerServer(event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (!isUpstreamPlayerChat(event)) return;

        if (event.isCommand()) {
            handleCommand(event);
            return;
        }

        ProxiedPlayer sender = (ProxiedPlayer) event.getSender();
        String serverName = getPlayerServerName(sender);

        ChatService.Result result = chatService.handleChat(
                sender.getUniqueId(), sender.getName(), event.getMessage(), serverName,
                msg -> sender.sendMessage(new TextComponent(StringUtil.unescapeNewlines(msg))));
        if (result == ChatService.Result.CANCELLED) event.setCancelled(true);
    }

    private void handleCommand(ChatEvent event) {
        if (!isUpstreamPlayerChat(event)) return;
        if (alreadyGatedByAsyncInterceptor(event)) return;
        ProxiedPlayer sender = (ProxiedPlayer) event.getSender();

        CommandInterceptService.CommandResult result = commandInterceptService.handleCommand(
                sender.getUniqueId(), sender.getName(),
                event.getMessage(), getPlayerServerName(sender));

        if (result != CommandInterceptService.CommandResult.ALLOWED) {
            event.setCancelled(true);
            String message = commandInterceptService.getBlockMessage(result, sender.getUniqueId());
            sender.sendMessage(new TextComponent(StringUtil.unescapeNewlines(message)));
        }
    }

    private boolean isUpstreamPlayerChat(ChatEvent event) {
        return event.getSender() instanceof ProxiedPlayer;
    }

    private boolean alreadyGatedByAsyncInterceptor(ChatEvent event) {
        return event.isCancelled();
    }

    private String getPlayerServerName(ProxiedPlayer player) {
        return player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";
    }

    private String extractIpAddress(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
        String addr = socketAddress.toString();
        if (addr.startsWith("/")) addr = addr.substring(1);
        if (addr.contains(":")) addr = addr.substring(0, addr.indexOf(":"));
        return addr;
    }
}
