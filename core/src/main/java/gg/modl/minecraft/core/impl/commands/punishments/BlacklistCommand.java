package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.util.PunishmentActionMessages;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class BlacklistCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("blacklist")
    @Syntax("<target> [reason...] [-silent] [-alt-blocking] [-stat-wipe]")
    @Conditions("permission:value=punishment.apply.blacklist")
    public void blacklist(CommandIssuer sender, @Name("target") Account target, @Default("") String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        // Parse arguments
        BlacklistArgs blacklistArgs = parseArguments(args);

        // Get issuer information
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Build punishment data
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("reason", blacklistArgs.reason.isEmpty() ? "No reason specified" : blacklistArgs.reason);
        dataMap.put("silent", blacklistArgs.silent);
        dataMap.put("altBlocking", blacklistArgs.altBlocking);
        dataMap.put("wipeAfterExpiry", blacklistArgs.statWipe);

        dataMap.put("issuedServer", sender.isPlayer()
            ? platform.getPlayerServer(sender.getUniqueId())
            : platform.getServerName());

        PunishmentCreateRequest v2Request = new PunishmentCreateRequest(
                target.getMinecraftUuid().toString(),
                issuerName,
                5, // Blacklist ordinal
                blacklistArgs.reason.isEmpty() ? "No reason specified" : blacklistArgs.reason,
                0L, // Blacklists are permanent
                dataMap,
                new ArrayList<>(),
                new ArrayList<>(),
                null, null
            );

            getHttpClient().createPunishmentWithResponse(v2Request).thenAccept(response -> {
                if (response.isSuccess()) {
                    String targetName = target.getUsernames().get(0).getUsername();

                    sender.sendMessage(localeManager.punishment()
                        .type("blacklist")
                        .target(targetName)
                        .punishmentId(response.getPunishmentId())
                        .get("general.punishment_issued"));

                    // Send action buttons if player
                    if (sender.isPlayer() && response.getPunishmentId() != null) {
                        platform.runOnMainThread(() -> {
                            PunishmentActionMessages.sendPunishmentActions(platform, sender.getUniqueId(), response.getPunishmentId());
                        });
                    }
                } else {
                    sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                        Map.of("error", localeManager.sanitizeErrorMessage(response.getMessage()))));
                }
            }).exceptionally(throwable -> {
                if (throwable.getCause() instanceof PanelUnavailableException) {
                    sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                } else {
                    sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                        Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
                }
                return null;
            });
    }

    private BlacklistArgs parseArguments(String args) {
        String[] arguments = args.split(" ");
        BlacklistArgs result = new BlacklistArgs();

        StringBuilder reasonBuilder = new StringBuilder();

        for (String arg : arguments) {
            if (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s")) {
                result.silent = true;
            } else if (arg.equalsIgnoreCase("-alt-blocking") || arg.equalsIgnoreCase("-ab")) {
                result.altBlocking = true;
            } else if (arg.equalsIgnoreCase("-stat-wipe") || arg.equalsIgnoreCase("-sw")) {
                result.statWipe = true;
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

    private static class BlacklistArgs {
        String reason = "";
        boolean silent = false;
        boolean altBlocking = false;
        boolean statWipe = false;
    }
}
