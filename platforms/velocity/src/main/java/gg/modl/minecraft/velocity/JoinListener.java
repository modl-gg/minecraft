package gg.modl.minecraft.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.login.LoginPipeline;
import gg.modl.minecraft.core.login.LoginService;
import gg.modl.minecraft.core.login.ProxyLoginFlow;
import gg.modl.minecraft.core.session.ServerSwitchService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

import java.util.UUID;

@RequiredArgsConstructor
public class JoinListener {
    private final Cache cache;
    private final Logger logger;
    private final VelocityPlatform platform;
    private final LoginPipeline loginPipeline;
    private final ProxyLoginFlow proxyLoginFlow;
    private final ServerSwitchService serverSwitchService;
    private final boolean debugMode;

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        try {
            String ipAddress = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
            return EventTask.resumeWhenComplete(proxyLoginFlow
                    .begin(event.getPlayer().getUniqueId(), event.getPlayer().getUsername(), ipAddress,
                            platform.getServerName())
                    .thenAccept(result -> applyLoginResult(event, result)));
        } catch (Throwable failure) {
            denyLogin(event, loginPipeline.getLoginService().unverifiableBanStatusMessage(), failure);
            return null;
        }
    }

    private void applyLoginResult(LoginEvent event, LoginService.LoginResult result) {
        try {
            String message = loginPipeline.getLoginService().denialMessage(result);
            if (message != null) denyLogin(event, message, null);
            else if (debugMode) logger.info("Allowed login for {}", event.getPlayer().getUsername());
        } catch (Throwable failure) {
            denyLogin(event, loginPipeline.getLoginService().unverifiableBanStatusMessage(), failure);
        }
    }

    private void denyLogin(LoginEvent event, String message, Throwable failure) {
        event.setResult(ResultedEvent.ComponentResult.denied(platform.toKickComponent(message)));
        if (failure == null) logger.warn("Login blocked for {}: {}", event.getPlayer().getUsername(), message);
        else logger.error("Login verification failed for {} - denying login", event.getPlayer().getUsername(), failure);
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        loginPipeline.getPlayerSessionService().handlePlayerJoin(uuid, event.getPlayer().getUsername());

        String texture = platform.getPlayerSkinTexture(uuid);
        if (texture != null) cache.cacheSkinTexture(uuid, texture);

        LoginCache.CachedLoginResult cachedResult = loginPipeline.getLoginCache().getCachedLoginResult(uuid);
        loginPipeline.getLoginService().cacheLoginData(uuid, cachedResult != null ? cachedResult.getResponse() : null);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        loginPipeline.getPlayerSessionService().handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        serverSwitchService.handleServerSwitch(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(),
                event.getServer().getServerInfo().getName());
    }

}
