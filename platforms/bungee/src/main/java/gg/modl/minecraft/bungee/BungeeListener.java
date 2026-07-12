package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.chat.ChatService;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.core.login.LoginService;
import gg.modl.minecraft.core.login.ProxyLoginFlow;
import gg.modl.minecraft.core.session.PlayerSessionService;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class BungeeListener implements Listener {
    private static final int LOGIN_EXECUTOR_MAX_THREADS = 4;
    private static final int LOGIN_EXECUTOR_QUEUE_CAPACITY = 64;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final AtomicInteger LOGIN_THREAD_COUNTER = new AtomicInteger();

    private final BungeePlatform platform;
    private final Cache cache;
    private final Plugin plugin;
    private final LoginCache loginCache;
    private final ChatService chatService;
    private final CommandInterceptService commandInterceptService;
    private final LoginService loginService;
    private final ProxyLoginFlow proxyLoginFlow;
    private final PlayerSessionService playerSessionService;
    private final ServerSwitchService serverSwitchService;
    private final ThreadPoolExecutor loginExecutor = createLoginExecutor();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        event.registerIntent(plugin);
        scheduleLoginCheck(event);
    }

    public void shutdown() {
        loginExecutor.shutdown();
        try {
            if (!loginExecutor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) loginExecutor.shutdownNow();
        } catch (InterruptedException e) {
            loginExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private ThreadPoolExecutor createLoginExecutor() {
        return new ThreadPoolExecutor(
                1,
                LOGIN_EXECUTOR_MAX_THREADS,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(LOGIN_EXECUTOR_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "modl-bungee-login-" + LOGIN_THREAD_COUNTER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private void runLoginCheck(LoginEvent event) {
        try {
            performLoginCheck(event);
        } catch (TimeoutException e) {
            platform.getLogger().warning("Login check timed out for " + event.getConnection().getName() + " - blocking login for safety");
            denyLogin(event, "Login verification timed out. Please try again.");
        } catch (Exception e) {
            handleLoginException(event, e);
        } finally {
            event.completeIntent(plugin);
        }
    }

    private void scheduleLoginCheck(LoginEvent event) {
        try {
            CompletableFuture.runAsync(() -> runLoginCheck(event), loginExecutor);
        } catch (RejectedExecutionException e) {
            platform.getLogger().warning("Login check executor rejected " + event.getConnection().getName() + " - blocking login for safety");
            denyLogin(event, "Login verification is temporarily unavailable. Please try again.");
            event.completeIntent(plugin);
        }
    }

    private void performLoginCheck(LoginEvent event) throws Exception {
        String ipAddress = extractIpAddress(event.getConnection().getSocketAddress());
        proxyLoginFlow.execute(
                event.getConnection().getUniqueId(),
                event.getConnection().getName(),
                ipAddress, platform.getServerName(),
                message -> denyLogin(event, message),
                () -> {});
    }

    private void handleLoginException(LoginEvent event, Exception e) {
        LoginService.LoginResult result = loginService.handleLoginError(e);
        if (result instanceof LoginService.LoginResult.Denied) {
            LoginService.LoginResult.Denied denied = (LoginService.LoginResult.Denied) result;
            platform.getLogger().warning("Login blocked for " + event.getConnection().getName() + ": " + denied.getMessage());
            denyLogin(event, denied.getMessage());
        } else platform.getLogger().severe("Failed to check punishments for " + event.getConnection().getName() + ": " + e.getMessage());
    }

    private void denyLogin(LoginEvent event, String reason) {
        event.setCancelReason(new TextComponent(reason));
        event.setCancelled(true);
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        playerSessionService.handlePlayerJoin(uuid, event.getPlayer().getName());

        String texture = platform.getPlayerSkinTexture(uuid);
        if (texture != null) cache.cacheSkinTexture(uuid, texture);

        LoginCache.CachedLoginResult cachedResult = loginCache.getCachedLoginResult(uuid);
        loginService.cacheLoginData(uuid, cachedResult != null ? cachedResult.getResponse() : null);
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        playerSessionService.handlePlayerDisconnect(
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
