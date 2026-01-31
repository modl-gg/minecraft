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
import lombok.RequiredArgsConstructor;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class BlacklistCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

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

        // Build punishment data as JsonObject
        JsonObject data = new JsonObject();
        data.addProperty("reason", blacklistArgs.reason.isEmpty() ? "No reason specified" : blacklistArgs.reason);
        data.addProperty("silent", blacklistArgs.silent);
        data.addProperty("altBlocking", blacklistArgs.altBlocking);
        data.addProperty("wipeAfterExpiry", blacklistArgs.statWipe);

        // Create manual punishment request for blacklist (ordinal 5)
        CreatePunishmentRequest request = new CreatePunishmentRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            5, // Blacklist ordinal
            blacklistArgs.reason.isEmpty() ? "No reason specified" : blacklistArgs.reason,
            0, // Blacklists are permanent (0 duration)
            data, // Pass the JsonObject with all punishment data
            new ArrayList<>(), // notes
            new ArrayList<>()  // attachedTicketIds
        );

        // Make copies for lambda usage
        final boolean silentBlacklist = blacklistArgs.silent;

        // Send manual punishment request (uses /minecraft/punishment/create endpoint)
        CompletableFuture<Void> future = httpClient.createPunishment(request);
        
        future.thenAccept(response -> {
            String targetName = target.getUsernames().get(0).getUsername();
            
            // Success message to issuer
            sender.sendMessage(localeManager.punishment()
                .type("blacklist")
                .target(targetName)
                .get("general.punishment_issued"));

            
            // Staff notification
            String staffMessage = localeManager.punishment()
                .issuer(issuerName)
                .type("blacklist")
                .target(targetName)
                .get("general.staff_notification");
            platform.staffBroadcast(staffMessage);
            
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

    /**
     * Get public notification message for blacklist using ordinal 5
     */
    private String getPublicNotificationMessage(String targetName) {
        Map<String, String> variables = Map.of("target", targetName);
        
        // Use ordinal 5 for blacklist (blacklists are always permanent)
        return localeManager.getPunishmentTypeMessage(5, "public_notification", variables);
    }

    private static class BlacklistArgs {
        String reason = "";
        boolean silent = false;
        boolean altBlocking = false;
        boolean statWipe = false;
    }
}