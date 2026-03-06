package gg.modl.minecraft.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import gg.modl.minecraft.core.config.StaffChatConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.util.ChatEventHandler;
import gg.modl.minecraft.core.util.MutedCommandUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.StringUtil;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ChatListener {
    private final VelocityPlatform platform;
    private final Cache cache;
    private final ChatMessageCache chatMessageCache;
    private final LocaleManager localeManager;
    private final List<String> mutedCommands;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final FreezeService freezeService;
    private final NetworkChatInterceptService networkChatInterceptService;
    private final ChatCommandLogService chatCommandLogService;
    private final StaffChatConfig staffChatConfig;

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerChat(PlayerChatEvent event) {
        String serverName = getPlayerServerName(event.getPlayer());

        var result = ChatEventHandler.handleChat(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(), event.getMessage(), serverName,
                msg -> event.getPlayer().sendMessage(Colors.get(StringUtil.unescapeNewlines(msg))),
                platform, cache, localeManager, chatMessageCache,
                staffChatService, staffChatConfig, chatManagementService,
                freezeService, chatCommandLogService, networkChatInterceptService);
        if (result == ChatEventHandler.Result.CANCELLED) event.setResult(PlayerChatEvent.ChatResult.denied());
    }

    // PostOrder.LATE (not LAST) is required for SignedVelocity compatibility
    @Subscribe(order = PostOrder.LATE)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) return;

        if (freezeService.isFrozen(player.getUniqueId())) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(Colors.get(localeManager.getMessage("freeze.command_blocked")));
            return;
        }

        chatCommandLogService.addCommand(
                player.getUniqueId().toString(), player.getUsername(),
                "/" + event.getCommand(), getPlayerServerName(player));

        if (!cache.isMuted(player.getUniqueId())) return;
        if (!MutedCommandUtil.isBlockedCommand(event.getCommand(), mutedCommands)) return;

        event.setResult(CommandExecuteEvent.CommandResult.denied());
        player.sendMessage(Colors.get(StringUtil.unescapeNewlines(
                PunishmentMessages.getMuteMessage(player.getUniqueId(), cache, localeManager))));
    }

    private String getPlayerServerName(Player player) {
        return player.getCurrentServer()
                .map(conn -> conn.getServerInfo().getName())
                .orElse("unknown");
    }

}