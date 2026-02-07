package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.ReportsMenu;
import gg.modl.minecraft.core.impl.menus.staff.StaffReportsMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Command to open the Reports Menu GUI.
 * With a player argument: opens the inspect Reports menu for that player
 * Without a player argument: opens the Staff Reports menu
 */
@RequiredArgsConstructor
public class ReportsCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("reports")
    @Syntax("[player]")
    @Description("Open the reports menu (for a player or all reports)")
    @Conditions("player|staff")
    public void reports(CommandIssuer sender, @Optional @Name("player") String playerQuery) {
        // Menus require V2 API
        if (httpClientHolder.getApiVersion() == ApiVersion.V1) {
            sender.sendMessage(localeManager.getMessage("api_errors.menus_require_v2"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        // If no player specified, open staff reports menu
        if (playerQuery == null || playerQuery.isEmpty()) {
            openStaffReportsMenu(sender, senderUuid);
            return;
        }

        // Player specified, open inspect reports menu
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        // Look up the player
        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                // Fetch full profile for the reports menu
                getHttpClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        platform.runOnMainThread(() -> {
                            // Get sender name
                            String senderName = "Staff";
                            if (platform.getPlayer(senderUuid) != null) {
                                senderName = platform.getPlayer(senderUuid).username();
                            }

                            // Open the reports menu (inspect version)
                            ReportsMenu menu = new ReportsMenu(
                                    platform,
                                    getHttpClient(),
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
                        sender.sendMessage(localeManager.getMessage("general.player_not_found"));
                    }
                }).exceptionally(throwable -> {
                    handleException(sender, throwable, playerQuery);
                    return null;
                });
            } else {
                sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            }
        }).exceptionally(throwable -> {
            handleException(sender, throwable, playerQuery);
            return null;
        });
    }

    private void openStaffReportsMenu(CommandIssuer sender, UUID senderUuid) {
        // Check if user has admin permissions
        boolean isAdmin = cache.hasPermission(senderUuid, "modl.admin");

        platform.runOnMainThread(() -> {
            // Get sender name
            String senderName = "Staff";
            if (platform.getPlayer(senderUuid) != null) {
                senderName = platform.getPlayer(senderUuid).username();
            }

            // Open the staff reports menu
            StaffReportsMenu menu = new StaffReportsMenu(
                    platform,
                    getHttpClient(),
                    senderUuid,
                    senderName,
                    isAdmin,
                    panelUrl,
                    null // No parent menu when opened from command
            );

            // Get CirrusPlayerWrapper and display
            CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
            menu.display(player);
        });
    }

    private void handleException(CommandIssuer sender, Throwable throwable, String playerQuery) {
        if (throwable.getCause() instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
        }
    }
}
