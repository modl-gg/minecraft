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
public class MuteCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("%cmd_mute")
    @Syntax("<target> [duration] [reason...] [-silent]")
    @Conditions("permission:value=punishment.apply.manual-mute")
    public void mute(CommandIssuer sender, @Name("target") Account target, @Default("") String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        // Parse arguments
        MuteArgs muteArgs = parseArguments(args);
        
        final String issuerName = gg.modl.minecraft.core.util.CommandUtil.resolveIssuerName(sender, cache, platform);

        // Build punishment data
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("reason", muteArgs.reason.isEmpty() ? "No reason specified" : muteArgs.reason);
        dataMap.put("silent", muteArgs.silent);
        if (muteArgs.duration > 0) {
            dataMap.put("duration", muteArgs.duration);
        }
        dataMap.put("issuedServer", sender.isPlayer()
            ? platform.getPlayerServer(sender.getUniqueId())
            : platform.getServerName());

        PunishmentCreateRequest request = new PunishmentCreateRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            1, // Manual mute ordinal
            muteArgs.reason.isEmpty() ? "No reason specified" : muteArgs.reason,
            muteArgs.duration,
            dataMap,
            new ArrayList<>(),
            new ArrayList<>(),
            null, null
        );

        getHttpClient().createPunishmentWithResponse(request).thenAccept(response -> {
            if (response.isSuccess()) {
                String targetName = target.getUsernames().get(0).getUsername();

                sender.sendMessage(localeManager.punishment()
                    .type("mute")
                    .target(targetName)
                    .duration(muteArgs.duration)
                    .punishmentId(response.getPunishmentId())
                    .get("general.punishment_issued"));

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

    private MuteArgs parseArguments(String args) {
        String[] arguments = args.split(" ");
        MuteArgs result = new MuteArgs();
        
        StringBuilder reasonBuilder = new StringBuilder();
        
        for (int i = 0; i < arguments.length; i++) {
            String arg = arguments[i];
            
            if (arg.equalsIgnoreCase("-silent") || arg.equalsIgnoreCase("-s")) {
                result.silent = true;
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

    private static class MuteArgs {
        String reason = "";
        long duration = 0;
        boolean silent = false;
    }
}