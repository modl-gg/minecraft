package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.InspectMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Command to open the Inspect Menu GUI for a player.
 */
@RequiredArgsConstructor
public class InspectCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("inspect|ins|check")
    @Syntax("<player>")
    @Description("Open the inspect menu for a player")
    public void inspect(CommandIssuer sender, @Name("player") String playerQuery) {
        // Must be a player to use this command
        if (!sender.isPlayer()) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        // TODO: Re-enable staff member check when permissions are properly configured
        // boolean isStaffMember = cache.isStaffMemberByPermissions(senderUuid);
        // if (!isStaffMember) {
        //     sender.sendMessage(localeManager.getMessage("player_lookup.permission_denied"));
        //     return;
        // }

        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        // Look up the player
        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClient.lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());
                String targetName = response.getData().getCurrentUsername();

                // Fetch full profile for the inspect menu
                httpClient.getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        platform.runOnMainThread(() -> {
                            // Get sender name
                            String senderName = "Staff";
                            if (platform.getPlayer(senderUuid) != null) {
                                senderName = platform.getPlayer(senderUuid).username();
                            }

                            // Open the inspect menu
                            InspectMenu menu = new InspectMenu(
                                    platform,
                                    httpClient,
                                    senderUuid,
                                    senderName,
                                    profileResponse.getProfile(),
                                    null // No parent menu when opened from command
                            );

                            // Get CirrusPlayerWrapper and display
                            CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                            menu.display(player);
                        });
                    } else {
                        sender.sendMessage(localeManager.getMessage("player_lookup.not_found", Map.of("player", playerQuery)));
                    }
                }).exceptionally(throwable -> {
                    handleException(sender, throwable, playerQuery);
                    return null;
                });
            } else {
                sender.sendMessage(localeManager.getMessage("player_lookup.not_found", Map.of("player", playerQuery)));
            }
        }).exceptionally(throwable -> {
            handleException(sender, throwable, playerQuery);
            return null;
        });
    }

    private void handleException(CommandIssuer sender, Throwable throwable, String playerQuery) {
        if (throwable.getCause() instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", throwable.getMessage())));
        }
    }
}
