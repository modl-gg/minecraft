package gg.modl.minecraft.velocity;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.login.LoginService;
import gg.modl.minecraft.core.login.ProxyLoginFlow;
import gg.modl.minecraft.core.session.PlayerSessionService;
import gg.modl.minecraft.core.session.ServerSwitchService;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.UUID;

@RequiredArgsConstructor
public class JoinListener {
    private final Cache cache;
    private final Logger logger;
    private final Platform platform;
    private final LoginCache loginCache;
    private final LoginService loginService;
    private final ProxyLoginFlow proxyLoginFlow;
    private final PlayerSessionService playerSessionService;
    private final ServerSwitchService serverSwitchService;
    private final boolean debugMode;

    @Subscribe
    public void onLogin(LoginEvent event) {
        String ipAddress = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        try {
            proxyLoginFlow.execute(
                    event.getPlayer().getUniqueId(),
                    event.getPlayer().getUsername(),
                    ipAddress,
                    platform.getServerName(),
                    message -> event.setResult(ResultedEvent.ComponentResult.denied(Colors.get(message))),
                    () -> {
                        event.setResult(ResultedEvent.ComponentResult.allowed());
                        if (debugMode) logger.info("Allowed login for {}", event.getPlayer().getUsername());
                    });
        } catch (Exception e) {
            LoginService.LoginResult errorResult = loginService.handleLoginError(e);
            if (errorResult instanceof LoginService.LoginResult.Denied) {
                LoginService.LoginResult.Denied denied = (LoginService.LoginResult.Denied) errorResult;
                logger.warn("Login blocked for {}: {}", event.getPlayer().getUsername(), denied.getMessage());
                event.setResult(ResultedEvent.ComponentResult.denied(
                        Component.text(denied.getMessage()).color(NamedTextColor.RED)));
            } else {
                logger.error("Failed to check punishments for {} - allowing login as fallback", event.getPlayer().getUsername(), e);
                event.setResult(ResultedEvent.ComponentResult.allowed());
            }
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerSessionService.handlePlayerJoin(uuid, event.getPlayer().getUsername());

        String texture = platform.getPlayerSkinTexture(uuid);
        if (texture != null) cache.cacheSkinTexture(uuid, texture);

        LoginCache.CachedLoginResult cachedResult = loginCache.getCachedLoginResult(uuid);
        loginService.cacheLoginData(uuid, cachedResult != null ? cachedResult.getResponse() : null);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        playerSessionService.handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        serverSwitchService.handleServerSwitch(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(),
                event.getServer().getServerInfo().getName());
    }

}
