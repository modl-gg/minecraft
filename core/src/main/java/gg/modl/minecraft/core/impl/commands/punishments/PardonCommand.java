package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PardonCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("pardon")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Pardon all of a player's active and unstarted punishments")
    @Conditions("permission:value=punishment.modify")
    public void pardon(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        if (isPunishmentId(target)) {
            tryPunishmentIdWithFallback(sender, target, issuerName, reason, null);
        } else {
            pardonAllByPlayerName(sender, target, issuerName, reason);
        }
    }

    @CommandAlias("unban")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Unban a player by name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void unban(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        if (isPunishmentId(target)) {
            tryPunishmentIdWithFallback(sender, target, issuerName, reason, "ban");
        } else {
            pardonSingleByPlayerName(sender, target, issuerName, reason, "ban");
        }
    }

    @CommandAlias("unmute")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Unmute a player by name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void unmute(CommandIssuer sender, @Name("target") String target, @Default("") String reason) {
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        if (isPunishmentId(target)) {
            tryPunishmentIdWithFallback(sender, target, issuerName, reason, "mute");
        } else {
            pardonSingleByPlayerName(sender, target, issuerName, reason, "mute");
        }
    }

    /**
     * /pardon <player> — pardon ALL active and unstarted punishments.
     */
    private void pardonAllByPlayerName(CommandIssuer sender, String playerName, String issuerName, String reason) {
        sender.sendMessage(localeManager.getMessage("pardon.processing_player", Map.of("player", playerName)));

        httpClient.getPlayer(new PlayerNameRequest(playerName)).thenAccept(response -> {
            if (!response.isSuccess() || response.getPlayer() == null) {
                sender.sendMessage(localeManager.getMessage("pardon.player_not_found", Map.of("player", playerName)));
                return;
            }

            Account account = response.getPlayer();
            List<Punishment> toPardon = account.getPunishments().stream()
                    .filter(this::isPardonable)
                    .filter(p -> p.isActive() || isUnstarted(p))
                    .collect(Collectors.toList());

            if (toPardon.isEmpty()) {
                sender.sendMessage(localeManager.getMessage("pardon.no_active_punishment",
                    Map.of("player", playerName, "type", "punishment")));
                return;
            }

            pardonPunishmentList(sender, toPardon, playerName, issuerName, reason, "all");
        }).exceptionally(throwable -> {
            handleLookupError(sender, throwable, playerName);
            return null;
        });
    }

    /**
     * /unban or /unmute <player> — pardon the 1 active punishment of that type,
     * or the oldest unstarted punishment of that type if no active ones exist.
     */
    private void pardonSingleByPlayerName(CommandIssuer sender, String playerName, String issuerName, String reason, String type) {
        sender.sendMessage(localeManager.getMessage("pardon.processing_player", Map.of("player", playerName)));

        httpClient.getPlayer(new PlayerNameRequest(playerName)).thenAccept(response -> {
            if (!response.isSuccess() || response.getPlayer() == null) {
                sender.sendMessage(localeManager.getMessage("pardon.player_not_found", Map.of("player", playerName)));
                return;
            }

            Account account = response.getPlayer();

            // Filter to pardonable punishments of the correct type
            List<Punishment> candidates = account.getPunishments().stream()
                    .filter(this::isPardonable)
                    .filter(p -> matchesType(p, type))
                    .collect(Collectors.toList());

            // First: find an active punishment (started and not expired)
            Punishment target = candidates.stream()
                    .filter(Punishment::isActive)
                    .findFirst()
                    .orElse(null);

            // If no active punishment, find the oldest unstarted one
            if (target == null) {
                target = candidates.stream()
                        .filter(this::isUnstarted)
                        .min(Comparator.comparing(Punishment::getIssued))
                        .orElse(null);
            }

            if (target == null) {
                sender.sendMessage(localeManager.getMessage("pardon.no_active_punishment",
                    Map.of("player", playerName, "type", type)));
                return;
            }

            pardonPunishmentList(sender, List.of(target), playerName, issuerName, reason, type);
        }).exceptionally(throwable -> {
            handleLookupError(sender, throwable, playerName);
            return null;
        });
    }

    /**
     * Pardon a list of punishments by ID, then send success/failure messages.
     */
    private void pardonPunishmentList(CommandIssuer sender, List<Punishment> punishments, String playerName, String issuerName, String reason, String type) {
        AtomicInteger pardoned = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int total = punishments.size();

        for (Punishment p : punishments) {
            PardonPunishmentRequest request = new PardonPunishmentRequest(
                p.getId(), issuerName, reason.isEmpty() ? null : reason, null
            );

            httpClient.pardonPunishment(request).thenAccept(pardonResponse -> {
                if (pardonResponse.hasPardoned()) {
                    pardoned.incrementAndGet();
                }
                checkCompletion(sender, pardoned, failed, total, playerName, issuerName, type);
            }).exceptionally(ex -> {
                failed.incrementAndGet();
                checkCompletion(sender, pardoned, failed, total, playerName, issuerName, type);
                return null;
            });
        }
    }

    /**
     * Check if all pardon requests completed and send the final message.
     */
    private void checkCompletion(CommandIssuer sender, AtomicInteger pardoned, AtomicInteger failed, int total, String playerName, String issuerName, String type) {
        if (pardoned.get() + failed.get() < total) {
            return; // Not all done yet
        }

        int pardonedCount = pardoned.get();
        if (pardonedCount > 0) {
            sender.sendMessage(localeManager.getMessage("pardon.success_player",
                Map.of("player", playerName, "type", type, "count", String.valueOf(pardonedCount))));

            String staffMessage = localeManager.getMessage("pardon.staff_notification_player",
                Map.of("issuer", issuerName, "player", playerName, "type", type, "count", String.valueOf(pardonedCount)));
            platform.staffBroadcast(staffMessage);

            invalidatePlayerCache(playerName, type);
        }

        if (failed.get() > 0 && pardonedCount == 0) {
            sender.sendMessage(localeManager.getMessage("pardon.error",
                Map.of("error", "Failed to pardon " + failed.get() + " punishment(s)")));
        }
    }

    /**
     * Check if a punishment is pardonable: not a kick, not already pardoned, not expired.
     */
    private boolean isPardonable(Punishment p) {
        // Skip kicks
        if (p.isKickType()) return false;

        // Check if already pardoned (data.active = false)
        Object activeFlag = p.getDataMap().get("active");
        if (activeFlag instanceof Boolean && !((Boolean) activeFlag)) {
            return false;
        }

        // Check for existing pardon modifications
        for (Modification mod : p.getModifications()) {
            if (mod.getType() == Modification.Type.MANUAL_PARDON || mod.getType() == Modification.Type.APPEAL_ACCEPT) {
                return false;
            }
        }

        // Check if expired
        Date expiry = p.getExpires();
        if (expiry != null && expiry.before(new Date())) {
            return false;
        }

        return true;
    }

    /**
     * Check if a punishment is unstarted: pardonable but started is null (for bans/mutes).
     */
    private boolean isUnstarted(Punishment p) {
        if (!isPardonable(p)) return false;
        return (p.isBanType() || p.isMuteType()) && p.getStarted() == null;
    }

    /**
     * Check if a punishment matches the expected type ("ban" or "mute").
     */
    private boolean matchesType(Punishment p, String type) {
        if ("ban".equals(type)) return p.isBanType();
        if ("mute".equals(type)) return p.isMuteType();
        return true;
    }

    // --- Punishment ID handling (unchanged) ---

    private void tryPunishmentIdWithFallback(CommandIssuer sender, String target, String issuerName, String reason, String expectedType) {
        PardonPunishmentRequest request = new PardonPunishmentRequest(
            target, issuerName, reason.isEmpty() ? null : reason, expectedType
        );

        httpClient.pardonPunishment(request).thenAccept(response -> {
            if (response.hasPardoned()) {
                sender.sendMessage(localeManager.getMessage("pardon.success_id",
                    Map.of("id", target)));

                String staffMessage = localeManager.getMessage("pardon.staff_notification_id",
                    Map.of("issuer", issuerName, "id", target));
                platform.staffBroadcast(staffMessage);

                cache.clear();
            } else {
                sender.sendMessage(localeManager.getMessage("pardon.already_pardoned_id",
                    Map.of("id", target)));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                String errorMessage = cause.getMessage();

                // Check if it's a "not found" error — fall back to player name
                if (errorMessage != null && (errorMessage.contains("No player found with punishment ID") ||
                    errorMessage.contains("Punishment with ID") || errorMessage.contains("not found") ||
                    errorMessage.contains("404"))) {
                    if (expectedType == null) {
                        pardonAllByPlayerName(sender, target, issuerName, reason);
                    } else {
                        pardonSingleByPlayerName(sender, target, issuerName, reason, expectedType);
                    }
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
            }
            return null;
        });
    }

    private boolean isPunishmentId(String target) {
        return target.length() == 8 && target.matches("^[A-F0-9]+$");
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

    private void handleLookupError(CommandIssuer sender, Throwable throwable, String playerName) {
        if (throwable.getCause() instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage("pardon.player_not_found", Map.of("player", playerName)));
        }
    }
}
