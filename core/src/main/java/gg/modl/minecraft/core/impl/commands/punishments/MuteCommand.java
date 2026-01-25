package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreatePunishmentRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.TimeUtil;
import lombok.RequiredArgsConstructor;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class MuteCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("mute")
    @Syntax("<target> [duration] [reason...] [-silent]")
    @Conditions("permission:value=punishment.apply.manual-mute")
    public void mute(CommandIssuer sender, @Name("target") Account target, @Default("") String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        // Parse arguments
        MuteArgs muteArgs = parseArguments(args);
        
        // Get issuer information
        final String issuerName = sender.isPlayer() ? 
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Build punishment data as JsonObject
        JsonObject data = new JsonObject();
        data.addProperty("reason", muteArgs.reason.isEmpty() ? "No reason specified" : muteArgs.reason);
        data.addProperty("silent", muteArgs.silent);
        if (muteArgs.duration > 0) {
            data.addProperty("duration", muteArgs.duration);
        }

        // Create manual punishment request for mute (ordinal 1)
        CreatePunishmentRequest request = new CreatePunishmentRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            1, // Manual mute ordinal
            muteArgs.reason.isEmpty() ? "No reason specified" : muteArgs.reason,
            muteArgs.duration,
            data, // Pass the JsonObject with all punishment data
            new ArrayList<>(), // notes
            new ArrayList<>()  // attachedTicketIds
        );

        // Make copies for lambda usage
        final boolean silentMute = muteArgs.silent;
        final String durationStr = muteArgs.duration > 0 ? TimeUtil.formatTimeMillis(muteArgs.duration) : "permanent";

        // Send manual punishment request (uses /minecraft/punishment/create endpoint)
        CompletableFuture<Void> future = httpClient.createPunishment(request);
        
        future.thenAccept(response -> {
            String targetName = target.getUsernames().get(0).getUsername();
            
            // Success message to issuer
            sender.sendMessage(localeManager.punishment()
                .type("mute")
                .target(targetName)
                .duration(muteArgs.duration)
                .get("general.punishment_issued"));

            
            // Staff notification
            String staffMessage = localeManager.punishment()
                .issuer(issuerName)
                .type("mute")
                .target(targetName)
                .get("general.staff_notification");
            platform.staffBroadcast(staffMessage);
            
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                    Map.of("error", throwable.getMessage())));
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

    /**
     * Get public notification message for manual mute using ordinal 1
     */
    private String getPublicNotificationMessage(String targetName, long duration) {
        boolean isTemporary = duration > 0;
        
        Map<String, String> variables = Map.of(
            "target", targetName,
            "duration", localeManager.formatDuration(duration)
        );
        
        // Use ordinal 1 for manual mute
        String messagePath = isTemporary ? "public_notification.temporary" : "public_notification.permanent";
        return localeManager.getPunishmentTypeMessage(1, messagePath, variables);
    }

    private static class MuteArgs {
        String reason = "";
        long duration = 0;
        boolean silent = false;
    }
}