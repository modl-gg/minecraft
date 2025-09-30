package gg.modl.minecraft.core.impl.commands.punishments;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreatePunishmentRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PermissionUtil;
import lombok.RequiredArgsConstructor;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class KickCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("kick")
    @Syntax("<target> [reason...] [-silent]")
    public void kick(CommandIssuer sender, @Name("target") Account target, @Default("") String args) {
        if (target == null) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.player_not_found", Map.of()));
            return;
        }

        // Check permission for kick
        if (!PermissionUtil.hasPermission(sender, cache, "punishment.apply.kick")) {
            sender.sendMessage(localeManager.getPunishmentMessage("general.no_permission_punishment",
                Map.of("type", "kick")));
            return;
        }

        // Parse arguments
        final KickArgs kickArgs = parseArguments(args);
        
        // Get issuer information
        final String issuerName = sender.isPlayer() ? 
            platform.getAbstractPlayer(sender.getUniqueId(), false).username() : "Console";

        // Build punishment data as JsonObject
        JsonObject data = new JsonObject();
        data.addProperty("reason", kickArgs.reason.isEmpty() ? "No reason specified" : kickArgs.reason);
        data.addProperty("silent", kickArgs.silent);

        // Create manual punishment request for kick (ordinal 0)
        CreatePunishmentRequest request = new CreatePunishmentRequest(
            target.getMinecraftUuid().toString(),
            issuerName,
            0, // Kick ordinal
            kickArgs.reason.isEmpty() ? "No reason specified" : kickArgs.reason,
            0, // Kicks have no duration
            data, // Pass the JsonObject with all punishment data
            new ArrayList<>(), // notes
            new ArrayList<>()  // attachedTicketIds
        );

        // Make copies for lambda usage
        final boolean silentKick = kickArgs.silent;

        // Send manual punishment request (uses /minecraft/punishment/create endpoint)
        CompletableFuture<Void> future = httpClient.createPunishment(request);
        
        future.thenAccept(response -> {
            String targetName = target.getUsernames().get(0).getUsername();
            
            // Success message to issuer
            sender.sendMessage(localeManager.punishment()
                .type("kick")
                .target(targetName)
                .get("general.punishment_issued"));

            // Staff notification
            if (!silentKick) {
                String staffMessage = localeManager.punishment()
                        .issuer(issuerName)
                        .type("kick")
                        .target(targetName)
                        .get("general.staff_notification");
                platform.staffBroadcast(staffMessage);
            }
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

    /**
     * Get public notification message for kick using ordinal 0
     */
    private String getPublicNotificationMessage(String targetName) {
        Map<String, String> variables = Map.of("target", targetName);
        
        // Use ordinal 0 for kick (kicks are always immediate, no duration)
        return localeManager.getPunishmentMessage("public_notification.default", variables);
    }

    private static class KickArgs {
        String reason = "";
        boolean silent = false;
    }
}