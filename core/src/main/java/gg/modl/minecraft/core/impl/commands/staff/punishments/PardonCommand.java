package gg.modl.minecraft.core.impl.commands.staff.punishments;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PardonPlayerRequest;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.regex.Pattern;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
@Command({"pardon", "unban", "unmute"})
public class PardonCommand {
    private static final Pattern PUNISHMENT_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");
    private static final int PUNISHMENT_ID_LENGTH = 8;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @Description("Pardon all of a player's active and unstarted punishments")
    @RequiresPermission("punishment.modify")
    public void pardon(CommandActor actor, @Named("target") String target, @Optional @ConsumeRemaining String reason) {
        reason = normalizeReason(reason);
        final String issuerName = CommandUtil.resolveActorName(actor, cache, platform);
        final String issuerId = CommandUtil.resolveActorId(actor, cache);

        if (isPunishmentId(target)) tryPunishmentIdWithFallback(actor, target, issuerName, issuerId, reason, null);
        else pardonByPlayerName(actor, target, issuerName, issuerId, reason, null);
    }

    @Description("Unban a player by name or punishment ID")
    @RequiresPermission("punishment.modify")
    public void unban(CommandActor actor, @Named("target") String target, @Optional @ConsumeRemaining String reason) {
        reason = normalizeReason(reason);
        final String issuerName = CommandUtil.resolveActorName(actor, cache, platform);
        final String issuerId = CommandUtil.resolveActorId(actor, cache);

        if (isPunishmentId(target)) tryPunishmentIdWithFallback(actor, target, issuerName, issuerId, reason, "ban");
        else pardonByPlayerName(actor, target, issuerName, issuerId, reason, "ban");
    }

    @Description("Unmute a player by name or punishment ID")
    @RequiresPermission("punishment.modify")
    public void unmute(CommandActor actor, @Named("target") String target, @Optional @ConsumeRemaining String reason) {
        reason = normalizeReason(reason);
        final String issuerName = CommandUtil.resolveActorName(actor, cache, platform);
        final String issuerId = CommandUtil.resolveActorId(actor, cache);

        if (isPunishmentId(target)) tryPunishmentIdWithFallback(actor, target, issuerName, issuerId, reason, "mute");
        else pardonByPlayerName(actor, target, issuerName, issuerId, reason, "mute");
    }

    private void pardonByPlayerName(CommandActor actor, String playerName, String issuerName, String issuerId, String reason, String type) {
        actor.reply(localeManager.getMessage("pardon.processing_player", mapOf("player", playerName)));

        String displayType = type != null ? type : "punishment";

        httpClientHolder.getClient().pardonPlayer(new PardonPlayerRequest(
            playerName, issuerName, issuerId, type, reason.isEmpty() ? null : reason
        )).thenAccept(response -> {
            if (response.hasPardoned()) {
                actor.reply(localeManager.getMessage("pardon.success_player",
                    mapOf("player", playerName, "type", displayType, "count", String.valueOf(response.getPardonedCount()))));
                invalidatePlayerCache(playerName, type);
            } else actor.reply(localeManager.getMessage("pardon.no_active_punishment",
                    mapOf("player", playerName, "type", displayType)));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager, "pardon.error");
            return null;
        });
    }

    private void tryPunishmentIdWithFallback(CommandActor actor, String target, String issuerName, String issuerId, String reason, String expectedType) {
        PardonPunishmentRequest request = new PardonPunishmentRequest(
            target, issuerName, issuerId, reason.isEmpty() ? null : reason, expectedType
        );

        httpClientHolder.getClient().pardonPunishment(request).thenAccept(response -> {
            if (response.hasPardoned()) {
                actor.reply(localeManager.getMessage("pardon.success_id",
                    mapOf("id", target)));
                cache.clear();
            } else actor.reply(localeManager.getMessage("pardon.already_pardoned_id",
                    mapOf("id", target)));
        }).exceptionally(throwable -> {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

            if (cause instanceof PanelUnavailableException) {
                actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
                return null;
            }

            String errorMessage = cause.getMessage();

            if (errorMessage != null && (errorMessage.contains("not found") || errorMessage.contains("404")))
                pardonByPlayerName(actor, target, issuerName, issuerId, reason, expectedType);
            else if (errorMessage != null && errorMessage.toLowerCase().contains("type")) {
                if ("ban".equals(expectedType)) actor.reply(localeManager.getMessage("pardon.error_wrong_type_ban",
                        mapOf("id", target)));
                else if ("mute".equals(expectedType)) actor.reply(localeManager.getMessage("pardon.error_wrong_type_mute",
                        mapOf("id", target)));
                else actor.reply(localeManager.getMessage("pardon.error",
                        mapOf("error", localeManager.sanitizeErrorMessage(errorMessage))));
            } else actor.reply(localeManager.getMessage("pardon.error",
                    mapOf("error", localeManager.sanitizeErrorMessage(errorMessage != null ? errorMessage : "Unknown error"))));
            return null;
        });
    }

    private boolean isPunishmentId(String target) {
        return target.length() == PUNISHMENT_ID_LENGTH && PUNISHMENT_ID_PATTERN.matcher(target).matches();
    }

    private static String normalizeReason(String reason) {
        return reason == null ? "" : reason.trim();
    }

    private void invalidatePlayerCache(String playerName, String type) {
        try {
            AbstractPlayer player = platform.getAbstractPlayer(playerName, false);
            if (player != null) {
                CachedProfile profile = cache.getPlayerProfile(player.getUuid());
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
