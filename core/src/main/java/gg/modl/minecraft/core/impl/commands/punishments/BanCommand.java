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
public class BanCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

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

        // Build punishment data as JsonObject
        JsonObject data = new JsonObject();
        data.addProperty("reason", banArgs.reason.isEmpty() ? "No reason specified" : banArgs.reason);
        data.addProperty("silent", banArgs.silent);
        data.addProperty("altBlocking", banArgs.altBlocking);
        data.addProperty("wipeAfterExpiry", banArgs.statWipe);
        if (banArgs.duration > 0) {
            data.addProperty("duration", banArgs.duration);
        }

        // Create manual punishment request for ban (ordinal 2)
        CreatePunishmentRequest request = new CreatePunishmentRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            2, // Manual ban ordinal
            banArgs.reason.isEmpty() ? "No reason specified" : banArgs.reason,
            banArgs.duration,
            data, // Pass the JsonObject with all punishment data
            new ArrayList<>(), // notes
            new ArrayList<>()  // attachedTicketIds
        );

        // Make copies for lambda usage
        final boolean silentBan = banArgs.silent;
        final String durationStr = banArgs.duration > 0 ? TimeUtil.formatTimeMillis(banArgs.duration) : "permanent";

        // Send manual punishment request (uses /minecraft/punishment/create endpoint)
        CompletableFuture<Void> future = httpClient.createPunishment(request);
        
        future.thenAccept(response -> {
            String targetName = target.getUsernames().get(0).getUsername();
            
            // Success message to issuer
            sender.sendMessage(localeManager.punishment()
                .type("ban")
                .target(targetName)
                .duration(banArgs.duration)
                .get("general.punishment_issued"));

            // Staff notification
            String staffMessage = localeManager.punishment()
                .issuer(issuerName)
                .type("ban")
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

    /**
     * Get public notification message for manual ban using ordinal 2
     */
    private String getPublicNotificationMessage(String targetName, long duration) {
        boolean isTemporary = duration > 0;
        
        Map<String, String> variables = Map.of(
            "target", targetName,
            "duration", localeManager.formatDuration(duration)
        );
        
        // Use ordinal 2 for manual ban
        String messagePath = isTemporary ? "public_notification.temporary" : "public_notification.permanent";
        return localeManager.getPunishmentTypeMessage(2, messagePath, variables);
    }

    private static class BanArgs {
        String reason = "";
        long duration = 0;
        boolean silent = false;
        boolean altBlocking = false;
        boolean statWipe = false;
    }
}