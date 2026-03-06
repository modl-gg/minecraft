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
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class WarnCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private static final String WARNING_NOTE_PREFIX = "WARNING: ";

    @CommandCompletion("@players")
    @CommandAlias("%cmd_warn")
    @Syntax("<target> <reason...> [-silent]")
    @Conditions("staff")
    public void warn(CommandIssuer sender, @Name("target") Account target, @Default("") String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        final WarnArgs warnArgs = parseArguments(args);
        if (warnArgs.reason.isEmpty()) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.invalid_syntax", Map.of()));
            return;
        }

        final String issuerName = CommandUtil.resolveIssuerName(sender, cache, platform);

        CreatePlayerNoteRequest noteRequest = new CreatePlayerNoteRequest(
            target.getMinecraftUuid().toString(), issuerName, WARNING_NOTE_PREFIX + warnArgs.reason
        );

        httpClientHolder.getClient().createPlayerNote(noteRequest).thenAccept(response -> {
            String targetName = target.getUsernames().get(0).getUsername();
            notifyTargetIfOnline(target, issuerName, warnArgs.reason);

            sender.sendMessage(localeManager.getMessage("warn.success", Map.of(
                "target", targetName, "reason", warnArgs.reason
            )));

            if (!warnArgs.silent) {
                platform.staffBroadcast(localeManager.getMessage("warn.staff_notification", Map.of(
                    "issuer", issuerName, "target", targetName, "reason", warnArgs.reason
                )));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            else sender.sendMessage(localeManager.getMessage("warn.error", Map.of(
                    "error", localeManager.sanitizeErrorMessage(throwable.getMessage())
                )));
            return null;
        });
    }

    private void notifyTargetIfOnline(Account target, String issuerName, String reason) {
        AbstractPlayer targetPlayer = platform.getAbstractPlayer(target.getMinecraftUuid(), false);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            platform.sendMessage(target.getMinecraftUuid(), localeManager.getMessage("warn.player_message", Map.of(
                "issuer", issuerName, "reason", reason
            )));
        }
    }

    private WarnArgs parseArguments(String args) {
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
