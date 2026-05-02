package gg.modl.minecraft.core.util;

import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class CommandUtil {
    private CommandUtil() {}

    public static String resolveActorName(CommandActor actor, Cache cache, Platform platform) {
        if (actor.uniqueId() == null) return "Console";
        String panelName = cache.getStaffDisplayName(actor.uniqueId());
        if (panelName != null) return panelName;
        return platform.getAbstractPlayer(actor.uniqueId(), false).getUsername();
    }

    public static String resolveActorId(CommandActor actor, Cache cache) {
        if (actor.uniqueId() == null) return null;
        return cache.getStaffId(actor.uniqueId());
    }

    public static String resolveSenderName(UUID uuid, Cache cache, Platform platform) {
        String name = cache.getStaffDisplayName(uuid);
        if (name != null) return name;
        AbstractPlayer player = platform.getPlayer(uuid);
        if (player != null) return player.getUsername();
        return "Staff";
    }

    public static Void handleApiError(CommandActor actor, Throwable throwable, LocaleManager localeManager) {
        return handleException(actor, throwable, localeManager, "general.punishment_error");
    }

    public static Void handleException(CommandActor actor, Throwable throwable, LocaleManager localeManager) {
        return handleException(actor, throwable, localeManager, "player_lookup.error");
    }

    public static Void handleException(CommandActor actor, Throwable throwable, LocaleManager localeManager, String errorKey) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof PanelUnavailableException) {
            actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            actor.reply(localeManager.getMessage(errorKey,
                    mapOf("error", localeManager.sanitizeErrorMessage(cause.getMessage() != null ? cause.getMessage() : "Unknown error"))));
        }
        return null;
    }
}
