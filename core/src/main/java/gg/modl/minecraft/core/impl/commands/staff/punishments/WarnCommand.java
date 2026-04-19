package gg.modl.minecraft.core.impl.commands.staff.punishments;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
@Command("warn")
public class WarnCommand {
    private static final String WARNING_NOTE_PREFIX = "WARNING: ";

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @StaffOnly
    public void warn(CommandActor actor, @Named("target") Account target, @Optional @ConsumeRemaining String args) {
        if (target == null) {
            actor.reply(localeManager.getPunishmentMessage("general.player_not_found", mapOf()));
            return;
        }

        final WarnArgs warnArgs = parseArguments(args);
        if (warnArgs.reason.isEmpty()) {
            actor.reply(localeManager.getPunishmentMessage("general.invalid_syntax", mapOf()));
            return;
        }

        final String issuerName = CommandUtil.resolveActorName(actor, cache, platform);
        final String issuerId = CommandUtil.resolveActorId(actor, cache);

        CreatePlayerNoteRequest noteRequest = new CreatePlayerNoteRequest(
            target.getMinecraftUuid().toString(), issuerName, issuerId, WARNING_NOTE_PREFIX + warnArgs.reason
        );

        httpClientHolder.getClient().createPlayerNote(noteRequest).thenAccept(response -> {
            String targetName = target.getUsernames().get(0).getUsername();
            notifyTargetIfOnline(target, issuerName, warnArgs.reason);

            actor.reply(localeManager.getMessage("warn.success", mapOf(
                "target", targetName, "reason", warnArgs.reason
            )));

            if (!warnArgs.silent) {
                platform.staffBroadcast(localeManager.getMessage("warn.staff_notification", mapOf(
                    "issuer", issuerName, "target", targetName, "reason", warnArgs.reason
                )));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
            else actor.reply(localeManager.getMessage("warn.error", mapOf(
                    "error", localeManager.sanitizeErrorMessage(throwable.getMessage())
                )));
            return null;
        });
    }

    private void notifyTargetIfOnline(Account target, String issuerName, String reason) {
        AbstractPlayer targetPlayer = platform.getAbstractPlayer(target.getMinecraftUuid(), false);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            platform.sendMessage(target.getMinecraftUuid(), localeManager.getMessage("warn.player_message", mapOf(
                "issuer", issuerName, "reason", reason
            )));
        }
    }

    private WarnArgs parseArguments(String args) {
        if (args == null || args.trim().isEmpty()) return new WarnArgs();
        String[] arguments = args.split(" ");
        WarnArgs result = new WarnArgs();
        StringBuilder reasonBuilder = new StringBuilder();

        for (String arg : arguments) {
            if (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s")) result.silent = true;
            else {
                if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
                reasonBuilder.append(arg);
            }
        }

        result.reason = reasonBuilder.toString().trim();
        return result;
    }

    private static class WarnArgs {
        String reason = "";
        boolean silent = false;
    }
}
