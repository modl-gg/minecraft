package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.util.CommandInterceptHandler;
import gg.modl.minecraft.core.util.StringUtil;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.List;

/**
 * Named class (not anonymous) to avoid BungeeCord EventBus reflection issues
 * where anonymous class methods become inaccessible on modern Java runtimes.
 */
@RequiredArgsConstructor
public class AsyncCommandInterceptor implements Listener {
    private static final String NAMESPACE_PREFIX = "modl:";

    private final AsyncCommandExecutor asyncExecutor;
    private final ProxyServer proxy;
    private final Cache cache;
    private final FreezeService freezeService;
    private final ChatCommandLogService chatCommandLogService;
    private final LocaleManager localeManager;
    private final List<String> mutedCommands;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(ChatEvent event) {
        if (event.isCancelled() || !event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (event.getMessage().length() <= 1) return;

        String stripped = event.getMessage().substring(1).trim();
        String baseCommand = stripped.split("\\s")[0].toLowerCase();

        if (asyncExecutor.isAsyncCommand(baseCommand) || asyncExecutor.isAsyncCommand(baseCommand.replace(NAMESPACE_PREFIX, ""))) {
            // Single-source the freeze/mute gate here for the async subset, because this LOWEST
            // handler dispatches the command before BungeeListener's HIGHEST gate can block it.
            String serverName = player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";
            CommandInterceptHandler.CommandResult result = CommandInterceptHandler.handleCommand(
                    player.getUniqueId(), player.getName(), event.getMessage(), serverName,
                    mutedCommands, cache, freezeService, chatCommandLogService);
            event.setCancelled(true);
            if (result != CommandInterceptHandler.CommandResult.ALLOWED) {
                String msg = CommandInterceptHandler.getBlockMessage(result, player.getUniqueId(), cache, localeManager);
                player.sendMessage(new TextComponent(StringUtil.unescapeNewlines(msg)));
                return;
            }
            asyncExecutor.execute(() ->
                    proxy.getPluginManager().dispatchCommand(player, stripped));
        }
    }
}
