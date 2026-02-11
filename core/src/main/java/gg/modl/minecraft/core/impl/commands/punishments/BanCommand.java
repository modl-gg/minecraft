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
import gg.modl.minecraft.core.util.TimeUtil;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class BanCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("ban")
    @Syntax("<target> [duration] [reason...] [-silent] [-alt-blocking] [-stat-wipe]")
    @Conditions("permission:value=punishment.apply.manual-ban")
    public void ban(CommandIssuer sender, @Name("target") Account target, @Default("") String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        // Parse arguments
        BanArgs banArgs = parseArguments(args);

        // Get issuer information
        final String issuerName = sender.isPlayer() ?
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Build punishment data
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("reason", banArgs.reason.isEmpty() ? "No reason specified" : banArgs.reason);
        dataMap.put("silent", banArgs.silent);
        dataMap.put("altBlocking", banArgs.altBlocking);
        dataMap.put("wipeAfterExpiry", banArgs.statWipe);
        if (banArgs.duration > 0) {
            dataMap.put("duration", banArgs.duration);
        }

        dataMap.put("issuedServer", sender.isPlayer()
            ? platform.getPlayerServer(sender.getUniqueId())
            : platform.getServerName());

        PunishmentCreateRequest v2Request = new PunishmentCreateRequest(
                target.getMinecraftUuid().toString(),
                issuerName,
                2, // Manual ban ordinal
                banArgs.reason.isEmpty() ? "No reason specified" : banArgs.reason,
                banArgs.duration,
                dataMap,
                new ArrayList<>(),
                new ArrayList<>(),
                null, null
            );

            getHttpClient().createPunishmentWithResponse(v2Request).thenAccept(response -> {
                if (response.isSuccess()) {
                    String targetName = target.getUsernames().get(0).getUsername();

                    sender.sendMessage(localeManager.punishment()
                        .type("ban")
                        .target(targetName)
                        .duration(banArgs.duration)
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

    private BanArgs parseArguments(String args) {
        String[] arguments = args.split(" ");
        BanArgs result = new BanArgs();

        StringBuilder reasonBuilder = new StringBuilder();

        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];

            if (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s")) {
                result.silent = true;
            } else if (arg.equalsIgnoreCase("-alt-blocking") || arg.equalsIgnoreCase("-ab")) {
                result.altBlocking = true;
            } else if (arg.equalsIgnoreCase("-stat-wipe") || arg.equalsIgnoreCase("-sw")) {
                result.statWipe = true;
            } else {
                // Check if this is a duration
                long duration = TimeUtil.getDuration(arg);
                if (duration != -1L && result.duration == 0) {
                    result.duration = duration;
                } else {
                    // Add to reason
                    if (reasonBuilder.length() > 0) {
                        reasonBuilder.append(" ");
                    }
                    reasonBuilder.append(arg);
                }
            }
        }

        result.reason = reasonBuilder.toString().trim();
        return result;
    }

    private static class BanArgs {
        String reason = "";
        long duration = 0;
        boolean silent = false;
        boolean altBlocking = false;
        boolean statWipe = false;
    }
}
