package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class WarnCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("warn")
    @Syntax("<target> <reason...> [-silent]")
    @Conditions("staff")
    public void warn(CommandIssuer sender, @Name("target") Account target, @Default("") String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        // Parse arguments
        final WarnArgs warnArgs = parseArguments(args);

        if (warnArgs.reason.isEmpty()) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.invalid_syntax", Map.of()));
            return;
        }

        // Get issuer information
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Create the note text for the profile
        final String noteText = String.format("WARNING: %s (by %s)", warnArgs.reason, issuerName);

        // Create note request
        CreatePlayerNoteRequest noteRequest = new CreatePlayerNoteRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            noteText
        );

        // Send the note creation request
        CompletableFuture<Void> future = httpClient.createPlayerNote(noteRequest);

        future.thenAccept(response -> {
            String targetName = target.getUsernames().get(0).getUsername();

            // Send warning message to the target player if they're online
            AbstractPlayer targetPlayer = platform.getAbstractPlayer(target.getMinecraftUuid(), false);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                String warningMessage = localeManager.getMessage("warn.player_message", Map.of(
                    "issuer", issuerName,
                    "reason", warnArgs.reason
                ));
                platform.sendMessage(target.getMinecraftUuid(), warningMessage);
            }

            // Success message to issuer
            sender.sendMessage(localeManager.getMessage("warn.success", Map.of(
                "target", targetName,
                "reason", warnArgs.reason
            )));

            // Staff notification (if not silent)
            if (!warnArgs.silent) {
                String staffMessage = localeManager.getMessage("warn.staff_notification", Map.of(
                    "issuer", issuerName,
                    "target", targetName,
                    "reason", warnArgs.reason
                ));
                platform.staffBroadcast(staffMessage);
            }

        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage(localeManager.getMessage("warn.error", Map.of(
                    "error", localeManager.sanitizeErrorMessage(throwable.getMessage())
                )));
            }
            return null;
        });
    }

    private WarnArgs parseArguments(String args) {
        String[] arguments = args.split(" ");
        WarnArgs result = new WarnArgs();

        StringBuilder reasonBuilder = new StringBuilder();

        for (String arg : arguments) {
            if (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s")) {
                result.silent = true;
            } else {
                // Add to reason
                if (reasonBuilder.length() > 0) {
                    reasonBuilder.append(" ");
                }
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
