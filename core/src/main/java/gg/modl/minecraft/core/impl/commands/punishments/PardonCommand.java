package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PardonPlayerRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class PardonCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("%cmd_pardon")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Pardon all of a player's active and unstarted punishments")
    @Conditions("permission:value=punishment.modify")
    public void pardon(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        if (isPunishmentId(target)) {
            tryPunishmentIdWithFallback(sender, target, issuerName, reason, null);
        } else {
            pardonByPlayerName(sender, target, issuerName, reason, null);
        }
    }

    @CommandAlias("%cmd_unban")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Unban a player by name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void unban(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        if (isPunishmentId(target)) {
            tryPunishmentIdWithFallback(sender, target, issuerName, reason, "ban");
        } else {
            pardonByPlayerName(sender, target, issuerName, reason, "ban");
        }
    }

    @CommandAlias("%cmd_unmute")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Unmute a player by name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void unmute(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        if (isPunishmentId(target)) {
            tryPunishmentIdWithFallback(sender, target, issuerName, reason, "mute");
        } else {
            pardonByPlayerName(sender, target, issuerName, reason, "mute");
        }
    }

    /**
     * Pardon by player name — delegates to the backend's bulk pardon endpoint.
     * @param type "ban", "mute", or null for all types
     */
    private void pardonByPlayerName(CommandIssuer sender, String playerName, String issuerName, String reason, String type) {
        sender.sendMessage(localeManager.getMessage("pardon.processing_player", Map.of("player", playerName)));

        String displayType = type != null ? type : "punishment";

        getHttpClient().pardonPlayer(new PardonPlayerRequest(
            playerName, issuerName, type, reason.isEmpty() ? null : reason
        )).thenAccept(response -> {
            if (response.hasPardoned()) {
                sender.sendMessage(localeManager.getMessage("pardon.success_player",
                    Map.of("player", playerName, "type", displayType, "count", String.valueOf(response.getPardonedCount()))));
                invalidatePlayerCache(playerName, type);
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.no_active_punishment",
                    Map.of("player", playerName, "type", displayType)));
            }
        }).exceptionally(throwable -> {
            handleError(sender, throwable);
            return null;
        });
    }

    // --- Punishment ID handling ---

    private void tryPunishmentIdWithFallback(CommandIssuer sender, String target, String issuerName, String reason, String expectedType) {
        PardonPunishmentRequest request = new PardonPunishmentRequest(
            target, issuerName, reason.isEmpty() ? null : reason, expectedType
        );

        getHttpClient().pardonPunishment(request).thenAccept(response -> {
            if (response.hasPardoned()) {
                sender.sendMessage(localeManager.getMessage("pardon.success_id",
                    Map.of("id", target)));
                cache.clear();
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.already_pardoned_id",
                    Map.of("id", target)));
            }
        }).exceptionally(throwable -> {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

            if (cause instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                return null;
            }

            String errorMessage = cause.getMessage();

            // Check if it's a "not found" error — fall back to player name lookup
            if (errorMessage != null && (errorMessage.contains("not found") || errorMessage.contains("404"))) {
                pardonByPlayerName(sender, target, issuerName, reason, expectedType);
            } else if (errorMessage != null && errorMessage.toLowerCase().contains("type")) {
                if ("ban".equals(expectedType)) {
                    sender.sendMessage(localeManager.getMessage("pardon.error_wrong_type_ban",
                        Map.of("id", target)));
                } else if ("mute".equals(expectedType)) {
                    sender.sendMessage(localeManager.getMessage("pardon.error_wrong_type_mute",
                        Map.of("id", target)));
                } else {
                    sender.sendMessage(localeManager.getMessage("pardon.error",
                        Map.of("error", localeManager.sanitizeErrorMessage(errorMessage))));
                }
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.error",
                    Map.of("error", localeManager.sanitizeErrorMessage(errorMessage != null ? errorMessage : "Unknown error"))));
            }
            return null;
        });
    }

    private boolean isPunishmentId(String target) {
        return target.length() == 8 && target.matches("^[A-Z0-9]+$");
    }

    private void invalidatePlayerCache(String playerName, String type) {
        try {
            AbstractPlayer player = platform.getAbstractPlayer(playerName, false);
            if (player != null) {
                if ("ban".equals(type)) {
                    cache.removeBan(player.uuid());
                } else if ("mute".equals(type)) {
                    cache.removeMute(player.uuid());
                } else {
                    cache.removeBan(player.uuid());
                    cache.removeMute(player.uuid());
                }
            }
        } catch (Exception e) {
            cache.clear();
        }
    }

    private void handleError(CommandIssuer sender, Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            String errorMessage = cause.getMessage();
            if (errorMessage != null && (errorMessage.contains("not found") || errorMessage.contains("404"))) {
                sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.error",
                    Map.of("error", localeManager.sanitizeErrorMessage(errorMessage != null ? errorMessage : "Unknown error"))));
            }
        }
    }
}
