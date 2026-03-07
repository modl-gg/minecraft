package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PardonPlayerRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class PardonCommand extends BaseCommand {
    private static final Pattern PUNISHMENT_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");
    private static final int PUNISHMENT_ID_LENGTH = 8;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("%cmd_pardon")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Pardon all of a player's active and unstarted punishments")
    @Conditions("permission:value=punishment.modify")
    public void pardon(CommandIssuer sender, @Name("target") String target, @Default() String reason) {
        final String issuerName = CommandUtil.resolveIssuerName(sender, cache, platform);

        if (isPunishmentId(target)) tryPunishmentIdWithFallback(sender, target, issuerName, reason, null);
        else pardonByPlayerName(sender, target, issuerName, reason, null);
    }

    @CommandAlias("%cmd_unban")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Unban a player by name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void unban(CommandIssuer sender, @Name("target") String target, @Default() String reason) {
        final String issuerName = CommandUtil.resolveIssuerName(sender, cache, platform);

        if (isPunishmentId(target)) tryPunishmentIdWithFallback(sender, target, issuerName, reason, "ban");
        else pardonByPlayerName(sender, target, issuerName, reason, "ban");
    }

    @CommandAlias("%cmd_unmute")
    @Syntax("<player/punishment_id> [reason...]")
    @Description("Unmute a player by name or punishment ID")
    @Conditions("permission:value=punishment.modify")
    public void unmute(CommandIssuer sender, @Name("target") String target, @Default() String reason) {
        final String issuerName = CommandUtil.resolveIssuerName(sender, cache, platform);

        if (isPunishmentId(target)) tryPunishmentIdWithFallback(sender, target, issuerName, reason, "mute");
        else pardonByPlayerName(sender, target, issuerName, reason, "mute");
    }

    private void pardonByPlayerName(CommandIssuer sender, String playerName, String issuerName, String reason, String type) {
        sender.sendMessage(localeManager.getMessage("pardon.processing_player", Map.of("player", playerName)));

        String displayType = type != null ? type : "punishment";

        httpClientHolder.getClient().pardonPlayer(new PardonPlayerRequest(
            playerName, issuerName, type, reason.isEmpty() ? null : reason
        )).thenAccept(response -> {
            if (response.hasPardoned()) {
                sender.sendMessage(localeManager.getMessage("pardon.success_player",
                    Map.of("player", playerName, "type", displayType, "count", String.valueOf(response.getPardonedCount()))));
                invalidatePlayerCache(playerName, type);
            } else sender.sendMessage(localeManager.getMessage("pardon.no_active_punishment",
                    Map.of("player", playerName, "type", displayType)));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(sender, throwable, localeManager, "pardon.error");
            return null;
        });
    }

    private void tryPunishmentIdWithFallback(CommandIssuer sender, String target, String issuerName, String reason, String expectedType) {
        PardonPunishmentRequest request = new PardonPunishmentRequest(
            target, issuerName, reason.isEmpty() ? null : reason, expectedType
        );

        httpClientHolder.getClient().pardonPunishment(request).thenAccept(response -> {
            if (response.hasPardoned()) {
                sender.sendMessage(localeManager.getMessage("pardon.success_id",
                    Map.of("id", target)));
                cache.clear();
            } else sender.sendMessage(localeManager.getMessage("pardon.already_pardoned_id",
                    Map.of("id", target)));
        }).exceptionally(throwable -> {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

            if (cause instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                return null;
            }

            String errorMessage = cause.getMessage();

            if (errorMessage != null && (errorMessage.contains("not found") || errorMessage.contains("404")))
                pardonByPlayerName(sender, target, issuerName, reason, expectedType);
            else if (errorMessage != null && errorMessage.toLowerCase().contains("type")) {
                if ("ban".equals(expectedType)) sender.sendMessage(localeManager.getMessage("pardon.error_wrong_type_ban",
                        Map.of("id", target)));
                else if ("mute".equals(expectedType)) sender.sendMessage(localeManager.getMessage("pardon.error_wrong_type_mute",
                        Map.of("id", target)));
                else sender.sendMessage(localeManager.getMessage("pardon.error",
                        Map.of("error", localeManager.sanitizeErrorMessage(errorMessage))));
            } else sender.sendMessage(localeManager.getMessage("pardon.error",
                    Map.of("error", localeManager.sanitizeErrorMessage(errorMessage != null ? errorMessage : "Unknown error"))));
            return null;
        });
    }

    private boolean isPunishmentId(String target) {
        return target.length() == PUNISHMENT_ID_LENGTH && PUNISHMENT_ID_PATTERN.matcher(target).matches();
    }

    private void invalidatePlayerCache(String playerName, String type) {
        try {
            AbstractPlayer player = platform.getAbstractPlayer(playerName, false);
            if (player != null) {
                gg.modl.minecraft.core.impl.cache.PlayerProfile profile = cache.getPlayerProfile(player.getUuid());
                if (profile != null) {
                    if ("ban".equals(type)) profile.setActiveBan(null);
                    else if ("mute".equals(type)) profile.setActiveMute(null);
                    else {
                        profile.setActiveBan(null);
                        profile.setActiveMute(null);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

}
