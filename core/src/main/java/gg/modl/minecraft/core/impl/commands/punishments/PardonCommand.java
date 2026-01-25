package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PardonPlayerRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.response.PardonResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class PardonCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("pardon")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Pardon a player's ban/mute by player name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void pardon(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        // Get issuer information
        final String issuerName = sender.isPlayer() ? 
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Always try punishment ID first, then fall back to player name
        tryPunishmentIdThenPlayerName(sender, target, issuerName, reason, null);
    }

    @CommandAlias("unban")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Unban a player by name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void unban(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        // Get issuer information
        final String issuerName = sender.isPlayer() ? 
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Always try punishment ID first, then fall back to player name
        tryPunishmentIdThenPlayerName(sender, target, issuerName, reason, "ban");
    }

    @CommandAlias("unmute")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Unmute a player by name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void unmute(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        // Get issuer information
        final String issuerName = sender.isPlayer() ? 
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Always try punishment ID first, then fall back to player name
        tryPunishmentIdThenPlayerName(sender, target, issuerName, reason, "mute");
    }

    private void pardonByPunishmentId(CommandIssuer sender, String punishmentId, String issuerName, String reason) {
        sender.sendMessage(localeManager.getMessage("pardon.processing_id", Map.of("id", punishmentId)));

        PardonPunishmentRequest request = new PardonPunishmentRequest(
            punishmentId,
            issuerName,
            reason.isEmpty() ? null : reason,
            null // No type checking for general pardon command
        );

        CompletableFuture<PardonResponse> future = httpClient.pardonPunishment(request);

        future.thenAccept(response -> {
            if (response.hasPardoned()) {
                sender.sendMessage(localeManager.getMessage("pardon.success_id",
                    Map.of("id", punishmentId)));

                // Staff notification - only if something was actually pardoned
                String staffMessage = localeManager.getMessage("pardon.staff_notification_id",
                    Map.of("issuer", issuerName, "id", punishmentId));
                platform.staffBroadcast(staffMessage);
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.already_pardoned_id",
                    Map.of("id", punishmentId)));
            }

        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.error",
                    Map.of("error", throwable.getMessage())));
            }
            return null;
        });
    }

    private void pardonByPlayerName(CommandIssuer sender, String playerName, String issuerName, String reason, String expectedType) {
        sender.sendMessage(localeManager.getMessage("pardon.processing_player", Map.of("player", playerName)));

        // Use the expected type if specified, otherwise default to ban
        String typeToPardon = expectedType != null ? expectedType : "ban";
        pardonSpecificType(sender, playerName, issuerName, reason, typeToPardon);
    }

    private void pardonSpecificType(CommandIssuer sender, String playerName, String issuerName, String reason, String type) {
        PardonPlayerRequest request = new PardonPlayerRequest(
            playerName,
            issuerName,
            type,
            reason.isEmpty() ? null : reason
        );

        CompletableFuture<PardonResponse> future = httpClient.pardonPlayer(request);

        future.thenAccept(response -> {
            if (response.hasPardoned()) {
                sender.sendMessage(localeManager.getMessage("pardon.success_player",
                    Map.of("player", playerName, "type", type)));

                // Staff notification - only if something was actually pardoned
                String staffMessage = localeManager.getMessage("pardon.staff_notification_player",
                    Map.of("issuer", issuerName, "player", playerName, "type", type));
                platform.staffBroadcast(staffMessage);

                // Invalidate cache for the pardoned player
                invalidatePlayerCache(playerName, type);
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.no_active_punishment",
                    Map.of("player", playerName, "type", type)));
            }

        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.error_player",
                    Map.of("player", playerName, "type", type, "error", throwable.getMessage())));
            }
            return null;
        });
    }

    private void pardonByPunishmentIdWithTypeCheck(CommandIssuer sender, String punishmentId, String issuerName, String reason, String expectedType) {
        sender.sendMessage(localeManager.getMessage("pardon.processing_id", Map.of("id", punishmentId)));

        // First, try to pardon with type validation on the backend
        PardonPunishmentRequest request = new PardonPunishmentRequest(
            punishmentId,
            issuerName,
            reason.isEmpty() ? null : reason,
            expectedType
        );

        CompletableFuture<PardonResponse> future = httpClient.pardonPunishment(request);
        
        future.thenAccept(response -> {
            sender.sendMessage(localeManager.getMessage("pardon.success_id", 
                Map.of("id", punishmentId)));
                
            // Staff notification
            String staffMessage = localeManager.getMessage("pardon.staff_notification_id",
                Map.of("issuer", issuerName, "id", punishmentId));
            platform.staffBroadcast(staffMessage);
            
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                String errorMessage = throwable.getMessage();
                
                // Check if it's a type mismatch error and provide helpful message
                if (errorMessage != null && errorMessage.toLowerCase().contains("type")) {
                    if ("ban".equals(expectedType)) {
                        sender.sendMessage(localeManager.getMessage("pardon.error_wrong_type_ban", 
                            Map.of("id", punishmentId)));
                    } else if ("mute".equals(expectedType)) {
                        sender.sendMessage(localeManager.getMessage("pardon.error_wrong_type_mute", 
                            Map.of("id", punishmentId)));
                    } else {
                        sender.sendMessage(localeManager.getMessage("pardon.error",
                            Map.of("error", errorMessage)));
                    }
                } else {
                    sender.sendMessage(localeManager.getMessage("pardon.error",
                        Map.of("error", errorMessage)));
                }
            }
            return null;
        });
    }

    private void tryPunishmentIdThenPlayerName(CommandIssuer sender, String target, String issuerName, String reason, String expectedType) {
        // First, try as punishment ID
        if (expectedType != null) {
            // Use type checking for specific commands (unban/unmute)
            tryPunishmentIdWithFallback(sender, target, issuerName, reason, expectedType);
        } else {
            // No type checking for general pardon command
            tryPunishmentIdWithFallback(sender, target, issuerName, reason, null);
        }
    }

    private void tryPunishmentIdWithFallback(CommandIssuer sender, String target, String issuerName, String reason, String expectedType) {
        PardonPunishmentRequest request = new PardonPunishmentRequest(
            target,
            issuerName,
            reason.isEmpty() ? null : reason,
            expectedType
        );

        CompletableFuture<PardonResponse> future = httpClient.pardonPunishment(request);

        future.thenAccept(response -> {
            if (response.hasPardoned()) {
                // Success - it was a punishment ID and it was pardoned
                sender.sendMessage(localeManager.getMessage("pardon.success_id",
                    Map.of("id", target)));

                // Staff notification - only if something was actually pardoned
                String staffMessage = localeManager.getMessage("pardon.staff_notification_id",
                    Map.of("issuer", issuerName, "id", target));
                platform.staffBroadcast(staffMessage);

                // For punishment ID pardons, we don't know the player name, so clear the entire cache
                // This is less efficient but ensures no stale cache remains
                cache.clear();
            } else {
                // Punishment ID was found but already inactive - inform user
                sender.sendMessage(localeManager.getMessage("pardon.already_pardoned_id",
                    Map.of("id", target)));
            }

        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                String errorMessage = throwable.getMessage();
                
                // Check if it's a "not found" error (meaning not a valid punishment ID)
                if (errorMessage != null && (errorMessage.contains("No player found with punishment ID") || 
                    errorMessage.contains("Punishment with ID") || errorMessage.contains("not found") || 
                    errorMessage.contains("404"))) {
                    // Fall back to player name
                    if (expectedType != null) {
                        // Specific type for unban/unmute commands - use pardonByPlayerName with expected type
                        pardonByPlayerName(sender, target, issuerName, reason, expectedType);
                    } else {
                        // General pardon - only try bans for player names
                        pardonByPlayerName(sender, target, issuerName, reason, null);
                    }
                } else if (errorMessage.toLowerCase().contains("type")) {
                    // Type validation error - show specific message
                    if ("ban".equals(expectedType)) {
                        sender.sendMessage(localeManager.getMessage("pardon.error_wrong_type_ban", 
                            Map.of("id", target)));
                    } else if ("mute".equals(expectedType)) {
                        sender.sendMessage(localeManager.getMessage("pardon.error_wrong_type_mute", 
                            Map.of("id", target)));
                    } else {
                        sender.sendMessage(localeManager.getMessage("pardon.error",
                            Map.of("error", errorMessage)));
                    }
                } else {
                    // Other error - show as-is
                    sender.sendMessage(localeManager.getMessage("pardon.error",
                        Map.of("error", errorMessage)));
                }
            }
            return null;
        });
    }

    /**
     * Check if the target string looks like a punishment ID
     * Punishment IDs are 8-character uppercase hexadecimal strings (first 8 chars of UUID)
     * Note: This is now only used for reference - we try punishment ID first regardless
     */
    private boolean isPunishmentId(String target) {
        // Punishment IDs are exactly 8 characters, uppercase hexadecimal
        return target.length() == 8 && target.matches("^[A-F0-9]+$");
    }
    
    /**
     * Invalidate cache for a specific player after successful pardon
     */
    private void invalidatePlayerCache(String playerName, String type) {
        try {
            // Try to get the player's UUID
            AbstractPlayer player = platform.getAbstractPlayer(playerName, false);
            if (player != null) {
                if ("ban".equals(type)) {
                    cache.removeBan(player.uuid());
                } else if ("mute".equals(type)) {
                    cache.removeMute(player.uuid());
                } else {
                    // For general pardon, remove both
                    cache.removeBan(player.uuid());
                    cache.removeMute(player.uuid());
                }
            }
        } catch (Exception e) {
            // If we can't get the player UUID, clear the entire cache to be safe
            cache.clear();
        }
    }
}