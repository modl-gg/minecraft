package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.AsyncCommandExecutor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Intercepts modl commands from ChatEvent and dispatches them asynchronously.
 * This is a named class (not anonymous) to avoid BungeeCord EventBus reflection
 * issues where anonymous class methods become inaccessible on modern Java runtimes.
 */
public class AsyncCommandInterceptor implements Listener {

    private final AsyncCommandExecutor asyncExecutor;
    private final ProxyServer proxy;

    public AsyncCommandInterceptor(AsyncCommandExecutor asyncExecutor, ProxyServer proxy) {
        this.asyncExecutor = asyncExecutor;
        this.proxy = proxy;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(ChatEvent event) {
        if (event.isCancelled() || !event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer player)) return;

        String message = event.getMessage();
        if (message.length() <= 1) return;

        String stripped = message.substring(1).trim();
        String baseCommand = stripped.split("\\s")[0].toLowerCase();

        if (asyncExecutor.isAsyncCommand(baseCommand) || asyncExecutor.isAsyncCommand(baseCommand.replace("modl:", ""))) {
            event.setCancelled(true);
            asyncExecutor.execute(() ->
                    proxy.getPluginManager().dispatchCommand((CommandSender) player, stripped));
        }
    }
}
