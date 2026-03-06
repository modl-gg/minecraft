package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.AsyncCommandExecutor;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Named class (not anonymous) to avoid BungeeCord EventBus reflection issues
 * where anonymous class methods become inaccessible on modern Java runtimes.
 */
@RequiredArgsConstructor
public class AsyncCommandInterceptor implements Listener {

    private static final String NAMESPACE_PREFIX = "modl:";

    private final AsyncCommandExecutor asyncExecutor;
    private final ProxyServer proxy;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(ChatEvent event) {
        if (event.isCancelled() || !event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer player)) return;
        if (event.getMessage().length() <= 1) return;

        String stripped = event.getMessage().substring(1).trim();
        String baseCommand = stripped.split("\\s")[0].toLowerCase();

        if (asyncExecutor.isAsyncCommand(baseCommand) || asyncExecutor.isAsyncCommand(baseCommand.replace(NAMESPACE_PREFIX, ""))) {
            event.setCancelled(true);
            asyncExecutor.execute(() ->
                    proxy.getPluginManager().dispatchCommand((CommandSender) player, stripped));
        }
    }
}
