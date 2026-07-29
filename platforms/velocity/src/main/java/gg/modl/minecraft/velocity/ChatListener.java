package gg.modl.minecraft.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import gg.modl.minecraft.core.chat.ChatService;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.core.util.StringUtil;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public class ChatListener {
    private final ChatService chatService;
    private final CommandInterceptService commandInterceptService;

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerChat(PlayerChatEvent event) {
        String serverName = getPlayerServerName(event.getPlayer());

        ChatService.Result result = chatService.handleChat(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(), event.getMessage(), serverName,
                msg -> event.getPlayer().sendMessage(Colors.get(StringUtil.unescapeNewlines(msg))));
        if (result == ChatService.Result.CANCELLED) event.setResult(PlayerChatEvent.ChatResult.denied());
    }

    @Subscribe(order = PostOrder.LATE)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;
        Player player = (Player) event.getCommandSource();

        CommandInterceptService.CommandResult result = commandInterceptService.handleCommand(
                player.getUniqueId(), player.getUsername(),
                "/" + event.getCommand(), getPlayerServerName(player));

        if (result != CommandInterceptService.CommandResult.ALLOWED) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            String blockMessage = StringUtil.unescapeNewlines(
                    commandInterceptService.getBlockMessage(result, player.getUniqueId()));
            player.sendMessage(Colors.get(Objects.requireNonNull(blockMessage)));
        }
    }

    private String getPlayerServerName(Player player) {
        return player.getCurrentServer()
                .map(conn -> conn.getServerInfo().getName())
                .orElse("unknown");
    }

}
