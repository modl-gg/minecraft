package gg.modl.minecraft.core.util;

import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.FreezeService;

import java.util.List;
import java.util.UUID;

/**
 * Shared command interception logic used by all platforms.
 * Handles frozen players, command logging, and muted command blocking.
 */
public final class CommandInterceptHandler {
    public enum CommandResult { ALLOWED, BLOCKED_FROZEN, BLOCKED_MUTED }

    /**
     * Checks whether a command should be blocked (frozen or muted player)
     * and logs the command. Returns the result for the platform to act on.
     */
    public static CommandResult handleCommand(
            UUID uuid, String username, String command, String serverName,
            List<String> mutedCommands, Cache cache, FreezeService freezeService,
            ChatCommandLogService chatCommandLogService) {

        if (freezeService.isFrozen(uuid)) {
            return CommandResult.BLOCKED_FROZEN;
        }

        chatCommandLogService.addCommand(uuid.toString(), username, command, serverName);

        if (cache.isMuted(uuid) && MutedCommandUtil.isBlockedCommand(command, mutedCommands)) {
            return CommandResult.BLOCKED_MUTED;
        }

        return CommandResult.ALLOWED;
    }

    /**
     * Returns the appropriate block message for the given result, or null if allowed.
     */
    public static String getBlockMessage(
            CommandResult result, UUID uuid, Cache cache, LocaleManager localeManager) {
        return switch (result) {
            case BLOCKED_FROZEN -> localeManager.getMessage("freeze.command_blocked");
            case BLOCKED_MUTED -> PunishmentMessages.getMuteMessage(uuid, cache, localeManager);
            case ALLOWED -> null;
        };
    }
}
