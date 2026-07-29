package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.core.util.StringUtil;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
@RequiredArgsConstructor
public class AsyncCommandInterceptor implements Listener {
    private static final String NAMESPACE_PREFIX = "modl:";

    private final AsyncCommandExecutor asyncExecutor;
    private final ProxyServer proxy;
    private final CommandInterceptService commandInterceptService;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(ChatEvent event) {
        if (event.isCancelled() || !event.isCommand()) return;
        if (!(event.getSender() instanceof ProxiedPlayer)) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        if (event.getMessage().length() <= 1) return;

        String stripped = event.getMessage().substring(1).trim();
        String baseCommand = stripped.split("\\s")[0].toLowerCase();

        if (asyncExecutor.isAsyncCommand(baseCommand) || asyncExecutor.isAsyncCommand(baseCommand.replace(NAMESPACE_PREFIX, ""))) {
            gateAndDispatchAsyncCommand(event, player, stripped);
        }
    }

    private void gateAndDispatchAsyncCommand(ChatEvent event, ProxiedPlayer player, String stripped) {
        String serverName = player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";
        CommandInterceptService.CommandResult result = commandInterceptService.handleCommand(
                player.getUniqueId(), player.getName(), event.getMessage(), serverName);
        event.setCancelled(true);
        if (result != CommandInterceptService.CommandResult.ALLOWED) {
            String msg = commandInterceptService.getBlockMessage(result, player.getUniqueId());
            player.sendMessage(new TextComponent(StringUtil.unescapeNewlines(msg)));
            return;
        }
        asyncExecutor.execute(() ->
                proxy.getPluginManager().dispatchCommand(player, stripped));
    }
}
