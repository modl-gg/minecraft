package gg.modl.minecraft.velocity;

import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.MutedCommandUtil;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.StringUtil;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.List;

public class ChatListener {
    
    private final VelocityPlatform platform;
    private final Cache cache;
    private final ChatMessageCache chatMessageCache;
    private final gg.modl.minecraft.core.locale.LocaleManager localeManager;
    private final List<String> mutedCommands;
    private final gg.modl.minecraft.core.service.StaffChatService staffChatService;
    private final gg.modl.minecraft.core.service.ChatManagementService chatManagementService;
    private final gg.modl.minecraft.core.service.FreezeService freezeService;
    private final gg.modl.minecraft.core.service.NetworkChatInterceptService networkChatInterceptService;
    private final gg.modl.minecraft.core.service.ChatCommandLogService chatCommandLogService;
    private final gg.modl.minecraft.core.config.StaffChatConfig staffChatConfig;

    public ChatListener(VelocityPlatform platform, Cache cache, ChatMessageCache chatMessageCache,
                        gg.modl.minecraft.core.locale.LocaleManager localeManager, List<String> mutedCommands,
                        gg.modl.minecraft.core.service.StaffChatService staffChatService,
                        gg.modl.minecraft.core.service.ChatManagementService chatManagementService,
                        gg.modl.minecraft.core.service.FreezeService freezeService,
                        gg.modl.minecraft.core.service.NetworkChatInterceptService networkChatInterceptService,
                        gg.modl.minecraft.core.service.ChatCommandLogService chatCommandLogService,
                        gg.modl.minecraft.core.config.StaffChatConfig staffChatConfig) {
        this.platform = platform;
        this.cache = cache;
        this.chatMessageCache = chatMessageCache;
        this.localeManager = localeManager;
        this.mutedCommands = mutedCommands;
        this.staffChatService = staffChatService;
        this.chatManagementService = chatManagementService;
        this.freezeService = freezeService;
        this.networkChatInterceptService = networkChatInterceptService;
        this.chatCommandLogService = chatCommandLogService;
        this.staffChatConfig = staffChatConfig;
    }
    
    @Subscribe(order = PostOrder.LATE)
    public void onPlayerChat(PlayerChatEvent event) {
        // Check for pending menu chat input FIRST
        if (ChatInputManager.handleChat(event.getPlayer().getUniqueId(), event.getMessage())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            return;
        }

        // Cache the chat message (before potentially cancelling for mute)
        // Use the player's current server name for cross-server compatibility
        String serverName = event.getPlayer().getCurrentServer().isPresent() ?
            event.getPlayer().getCurrentServer().get().getServerInfo().getName() : "unknown";
        chatMessageCache.addMessage(
            serverName,
            event.getPlayer().getUniqueId().toString(),
            event.getPlayer().getUsername(),
            event.getMessage()
        );

        // Staff chat: if sender is in staff chat mode, redirect to staff chat
        if (staffChatService.isInStaffChat(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            String inGameName = event.getPlayer().getUsername();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            platform.staffBroadcast(staffChatConfig.formatMessage(inGameName, panelName, event.getMessage()));
            return;
        }

        // Staff chat prefix shortcut
        if (staffChatConfig.isEnabled() && event.getMessage().startsWith(staffChatConfig.getPrefix())
                && PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            String msg = event.getMessage().substring(staffChatConfig.getPrefix().length()).trim();
            if (!msg.isEmpty()) {
                String inGameName = event.getPlayer().getUsername();
                String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
                platform.staffBroadcast(staffChatConfig.formatMessage(inGameName, panelName, msg));
            }
            return;
        }

        // Chat management: chat disabled check
        boolean isStaff = PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache);
        if (!chatManagementService.canSendMessage(event.getPlayer().getUniqueId(), isStaff)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            if (!chatManagementService.isChatEnabled()) {
                event.getPlayer().sendMessage(Colors.get(localeManager.getMessage("chat_management.chat_disabled")));
            } else {
                int remaining = chatManagementService.getSlowModeRemaining(event.getPlayer().getUniqueId());
                event.getPlayer().sendMessage(Colors.get(localeManager.getMessage("chat_management.slow_mode_wait",
                        java.util.Map.of("seconds", String.valueOf(remaining)))));
            }
            return;
        }

        if (cache.isMuted(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            event.getPlayer().sendMessage(Colors.get(StringUtil.unescapeNewlines(
                    PunishmentMessages.getMuteMessage(event.getPlayer().getUniqueId(), cache, localeManager))));
            return;
        }

        // Freeze: redirect frozen player chat to staff
        if (freezeService.isFrozen(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            platform.staffBroadcast(localeManager.getMessage("freeze.frozen_chat",
                    java.util.Map.of("player", event.getPlayer().getUsername(), "message", event.getMessage())));
            return;
        }

        // Log chat message
        chatCommandLogService.addChatMessage(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getUsername(),
                event.getMessage(),
                serverName
        );

        // Forward to interceptors
        for (java.util.UUID interceptor : networkChatInterceptService.getInterceptors()) {
            if (!interceptor.equals(event.getPlayer().getUniqueId())) {
                platform.sendMessage(interceptor, localeManager.getMessage("intercept.message",
                        java.util.Map.of("player", event.getPlayer().getUsername(), "message", event.getMessage())));
            }
        }
    }

    // don't set this shit to LAST, it WILL not work with SignedVelocity lol
    @Subscribe(order = PostOrder.LATE)
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getCommandSource();

        // Freeze: block all commands for frozen players
        if (freezeService.isFrozen(player.getUniqueId())) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(Colors.get(localeManager.getMessage("freeze.command_blocked")));
            return;
        }

        // Log command
        String cmdServerName = player.getCurrentServer().isPresent() ?
            player.getCurrentServer().get().getServerInfo().getName() : "unknown";
        chatCommandLogService.addCommand(
                player.getUniqueId().toString(),
                player.getUsername(),
                "/" + event.getCommand(),
                cmdServerName
        );

        if (!cache.isMuted(player.getUniqueId())) {
            return;
        }
        // event.getCommand() returns the command without leading slash
        if (!MutedCommandUtil.isBlockedCommand(event.getCommand(), mutedCommands)) {
            return;
        }

        event.setResult(CommandExecuteEvent.CommandResult.denied());
        player.sendMessage(Colors.get(StringUtil.unescapeNewlines(
                PunishmentMessages.getMuteMessage(player.getUniqueId(), cache, localeManager))));
    }

    public ChatMessageCache getChatMessageCache() {
        return chatMessageCache;
    }
}