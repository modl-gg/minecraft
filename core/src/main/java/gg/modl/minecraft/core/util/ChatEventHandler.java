package gg.modl.minecraft.core.util;

import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.StaffChatConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.service.StaffChatService;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared chat event processing pipeline used by all platforms.
 */
public final class ChatEventHandler {

    private ChatEventHandler() {}

    public enum Result {
        CANCELLED,
        ALLOWED
    }

    public static Result handleChat(
            UUID senderUuid, String senderName, String message, String serverName,
            Consumer<String> sendMessage,
            Platform platform, Cache cache, LocaleManager localeManager,
            ChatMessageCache chatMessageCache,
            StaffChatService staffChatService, StaffChatConfig staffChatConfig,
            ChatManagementService chatManagementService,
            FreezeService freezeService,
            ChatCommandLogService chatCommandLogService,
            NetworkChatInterceptService networkChatInterceptService) {

        if (ChatInputManager.handleChat(senderUuid, message)) return Result.CANCELLED;

        chatMessageCache.addMessage(serverName, senderUuid.toString(), senderName, message);

        if (staffChatService.isInStaffChat(senderUuid)) {
            String panelName = cache.getStaffDisplayName(senderUuid);
            platform.staffBroadcast(staffChatConfig.formatMessage(senderName, panelName, message));
            return Result.CANCELLED;
        }

        if (staffChatConfig.isEnabled() && message.startsWith(staffChatConfig.getPrefix())
                && PermissionUtil.isStaff(senderUuid, cache)) {
            String msg = message.substring(staffChatConfig.getPrefix().length()).trim();
            if (!msg.isEmpty()) {
                String panelName = cache.getStaffDisplayName(senderUuid);
                platform.staffBroadcast(staffChatConfig.formatMessage(senderName, panelName, msg));
            }
            return Result.CANCELLED;
        }

        boolean isStaff = PermissionUtil.isStaff(senderUuid, cache);
        if (!chatManagementService.canSendMessage(senderUuid, isStaff)) {
            if (!chatManagementService.isChatEnabled()) sendMessage.accept(localeManager.getMessage("chat_management.chat_disabled"));
            else {
                int remaining = chatManagementService.getSlowModeRemaining(senderUuid);
                sendMessage.accept(localeManager.getMessage("chat_management.slow_mode_wait",
                        Map.of("seconds", String.valueOf(remaining))));
            }
            return Result.CANCELLED;
        }

        if (cache.isMuted(senderUuid)) {
            sendMessage.accept(PunishmentMessages.getMuteMessage(senderUuid, cache, localeManager));
            return Result.CANCELLED;
        }

        if (freezeService.isFrozen(senderUuid)) {
            platform.staffBroadcast(localeManager.getMessage("freeze.frozen_chat",
                    Map.of("player", senderName, "message", message)));
            return Result.CANCELLED;
        }

        chatCommandLogService.addChatMessage(senderUuid.toString(), senderName, message, serverName);

        for (UUID interceptor : networkChatInterceptService.getInterceptors()) {
            if (!interceptor.equals(senderUuid)) {
                platform.sendMessage(interceptor, localeManager.getMessage("intercept.message",
                        Map.of("player", senderName, "message", message)));
            }
        }

        return Result.ALLOWED;
    }
}
