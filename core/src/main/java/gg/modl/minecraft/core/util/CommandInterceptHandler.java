package gg.modl.minecraft.core.util;

import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.FreezeService;

import java.util.List;
import java.util.UUID;

public final class CommandInterceptHandler {
    private CommandInterceptHandler() {}

    public enum CommandResult { ALLOWED, BLOCKED_FROZEN, BLOCKED_MUTED }

    public static CommandResult handleCommand(
            UUID uuid, String username, String command, String serverName,
            List<String> mutedCommands, Cache cache, FreezeService freezeService,
            ChatCommandLogService chatCommandLogService) {

        if (freezeService.isFrozen(uuid)) {
            return CommandResult.BLOCKED_FROZEN;
        }

        chatCommandLogService.addCommand(uuid.toString(), username, command, serverName);

        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile != null && profile.isMuted() && MutedCommandUtil.isBlockedCommand(command, mutedCommands)) {
            return CommandResult.BLOCKED_MUTED;
        }

        return CommandResult.ALLOWED;
    }

    public static String getBlockMessage(
            CommandResult result, UUID uuid, Cache cache, LocaleManager localeManager) {
        if (result == CommandResult.BLOCKED_FROZEN) {
            return localeManager.getMessage("freeze.command_blocked");
        } else if (result == CommandResult.BLOCKED_MUTED) {
            CachedProfile profile = cache.getPlayerProfile(uuid);
            return PunishmentMessages.getMuteMessage(profile != null ? profile.getActiveMute() : null, localeManager);
        } else {
            return null;
        }
    }
}
