package gg.modl.minecraft.core.chat;

import gg.modl.minecraft.core.PluginServices;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.config.ConfigManager.StaffChatConfig;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.punishment.PunishmentMessageService;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.staff.PermissionUtil;

import java.util.UUID;
import java.util.function.Consumer;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class ChatService {
    public enum Result {
        CANCELLED,
        ALLOWED
    }

    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final ChatMessageCache chatMessageCache;
    private final StaffChatService staffChatService;
    private final StaffChatConfig staffChatConfig;
    private final ChatManagementService chatManagementService;
    private final FreezeService freezeService;
    private final ChatCommandLogService chatCommandLogService;
    private final NetworkChatInterceptService networkChatInterceptService;
    private final PunishmentMessageService punishmentMessageService;

    public ChatService(Platform platform, Cache cache, LocaleManager localeManager, ChatMessageCache chatMessageCache,
                       StaffChatService staffChatService, StaffChatConfig staffChatConfig,
                       ChatManagementService chatManagementService, FreezeService freezeService,
                       ChatCommandLogService chatCommandLogService, NetworkChatInterceptService networkChatInterceptService,
                       PunishmentMessageService punishmentMessageService) {
        this.platform = platform;
        this.cache = cache;
        this.localeManager = localeManager;
        this.chatMessageCache = chatMessageCache;
        this.staffChatService = staffChatService;
        this.staffChatConfig = staffChatConfig;
        this.chatManagementService = chatManagementService;
        this.freezeService = freezeService;
        this.chatCommandLogService = chatCommandLogService;
        this.networkChatInterceptService = networkChatInterceptService;
        this.punishmentMessageService = punishmentMessageService;
    }

    public Result handleChat(UUID senderUuid, String senderName, String message, String serverName,
                             Consumer<String> sendMessage) {
        if (PluginServices.chatInput().handleChat(senderUuid, message)) return Result.CANCELLED;

        chatMessageCache.updatePlayerServer(serverName, senderUuid.toString());

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
            if (!chatManagementService.isChatEnabled()) {
                sendMessage.accept(localeManager.getMessage("chat_management.chat_disabled"));
            } else {
                int remaining = chatManagementService.getSlowModeRemaining(senderUuid);
                sendMessage.accept(localeManager.getMessage("chat_management.slow_mode_wait",
                        mapOf("seconds", String.valueOf(remaining))));
            }
            return Result.CANCELLED;
        }

        CachedProfile senderProfile = cache.getPlayerProfile(senderUuid);
        if (senderProfile != null && senderProfile.isMuted()) {
            sendMessage.accept(punishmentMessageService.getMuteMessage(senderProfile.getActiveMute()));
            return Result.CANCELLED;
        }

        if (freezeService.isFrozen(senderUuid)) {
            String frozenChat = localeManager.getMessage("freeze.frozen_chat",
                    mapOf("player", senderName, "message", message));
            platform.staffBroadcast(frozenChat);
            sendMessage.accept(frozenChat);
            return Result.CANCELLED;
        }

        chatMessageCache.addMessage(serverName, senderUuid.toString(), senderName, message);
        chatCommandLogService.addChatMessage(senderUuid.toString(), senderName, message, serverName);

        for (UUID interceptor : networkChatInterceptService.getInterceptors()) {
            if (!interceptor.equals(senderUuid)) {
                platform.sendMessage(interceptor, localeManager.getMessage("intercept.message",
                        mapOf("player", senderName, "message", message)));
            }
        }

        if (!isStaff && !chatManagementService.recordMessageSent(senderUuid)) {
            int remaining = chatManagementService.getSlowModeRemaining(senderUuid);
            sendMessage.accept(localeManager.getMessage("chat_management.slow_mode_wait",
                    mapOf("seconds", String.valueOf(remaining))));
            return Result.CANCELLED;
        }

        return Result.ALLOWED;
    }
}
