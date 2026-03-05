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
public class KickCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("%cmd_kick")
    @Syntax("<target> [reason...] [-silent]")
    @Conditions("permission:value=punishment.apply.kick")
    public void kick(CommandIssuer sender, @Name("target") Account target, @Default("") String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        // Parse arguments
        final KickArgs kickArgs = parseArguments(args);
        
        final String issuerName = gg.modl.minecraft.core.util.CommandUtil.resolveIssuerName(sender, cache, platform);

        // Build punishment data
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("reason", kickArgs.reason.isEmpty() ? "No reason specified" : kickArgs.reason);
        dataMap.put("silent", kickArgs.silent);
        dataMap.put("issuedServer", sender.isPlayer()
            ? platform.getPlayerServer(sender.getUniqueId())
            : platform.getServerName());

        PunishmentCreateRequest request = new PunishmentCreateRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            0, // Kick ordinal
            kickArgs.reason.isEmpty() ? "No reason specified" : kickArgs.reason,
            0L, // Kicks have no duration
            dataMap,
            new ArrayList<>(),
            new ArrayList<>(),
            null, null
        );

        getHttpClient().createPunishmentWithResponse(request).thenAccept(response -> {
            if (response.isSuccess()) {
                String targetName = target.getUsernames().get(0).getUsername();

                sender.sendMessage(localeManager.punishment()
                    .type("kick")
                    .target(targetName)
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

    private KickArgs parseArguments(String args) {
        String[] arguments = args.split(" ");
        KickArgs result = new KickArgs();
        
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

    private static class KickArgs {
        String reason = "";
        boolean silent = false;
    }
}