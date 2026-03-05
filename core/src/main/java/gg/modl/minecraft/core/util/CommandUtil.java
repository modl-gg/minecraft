package gg.modl.minecraft.core.util;

import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Map;

public final class CommandUtil {

    private CommandUtil() {}

    /**
     * Resolve the display name for a command issuer.
     * Returns panel username if available, in-game name for players, "Console" for console.
     */
    public static String resolveIssuerName(CommandIssuer sender, Cache cache, Platform platform) {
        if (!sender.isPlayer()) return "Console";
        String panelName = cache.getStaffDisplayName(sender.getUniqueId());
        if (panelName != null) return panelName;
        return platform.getAbstractPlayer(sender.getUniqueId(), false).username();
    }

    /**
     * Handle API errors in command exceptionally() blocks.
     * Returns a Function suitable for CompletableFuture.exceptionally().
     */
    public static Void handleApiError(CommandIssuer sender, Throwable throwable, LocaleManager localeManager) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                    Map.of("error", localeManager.sanitizeErrorMessage(cause.getMessage()))));
        }
        return null;
    }
}
