package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.AltsMenu;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Command to open the Alts Menu GUI for a player,
 * or print linked accounts to chat with the -p flag.
 */
@RequiredArgsConstructor
public class AltsCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("alts|alt")
    @Syntax("<player> [-p]")
    @Description("Open the alts menu for a player, or use -p to print to chat")
    @Conditions("player|staff")
    public void alts(CommandIssuer sender, @Name("player") String playerQuery, @Default("") String flags) {
        boolean printMode = flags.equalsIgnoreCase("-p") || flags.equalsIgnoreCase("print");

        // Console always uses print mode
        if (!sender.isPlayer()) {
            printAlts(sender, playerQuery);
            return;
        }

        if (printMode) {
            printAlts(sender, playerQuery);
            return;
        }

        // GUI mode - requires V2 API
        if (httpClientHolder.getApiVersion() == ApiVersion.V1) {
            sender.sendMessage(localeManager.getMessage("api_errors.menus_require_v2"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        // Look up the player
        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                // Fetch full profile for the alts menu
                getHttpClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        platform.runOnMainThread(() -> {
                            // Get sender name
                            String senderName = "Staff";
                            if (platform.getPlayer(senderUuid) != null) {
                                senderName = platform.getPlayer(senderUuid).username();
                            }

                            // Open the alts menu
                            AltsMenu menu = new AltsMenu(
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

    private void printAlts(CommandIssuer sender, String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                getHttpClient().getLinkedAccounts(targetUuid).thenAccept(linkedResponse -> {
                    displayAlts(sender, playerName, linkedResponse.getLinkedAccounts());
                }).exceptionally(throwable -> {
                    if (throwable.getCause() instanceof PanelUnavailableException) {
                        sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                    } else {
                        // Show empty list on error
                        displayAlts(sender, playerName, List.of());
                    }
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

    private void displayAlts(CommandIssuer sender, String playerName, List<Account> linkedAccounts) {
        sender.sendMessage(localeManager.getMessage("print.alts.header", Map.of("player", playerName)));

        if (linkedAccounts == null || linkedAccounts.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("print.alts.empty"));
        } else {
            int ordinal = 1;
            for (Account account : linkedAccounts) {
                String username = "Unknown";
                if (account.getUsernames() != null && !account.getUsernames().isEmpty()) {
                    username = account.getUsernames().get(account.getUsernames().size() - 1).getUsername();
                }

                String uuid = account.getMinecraftUuid() != null ? account.getMinecraftUuid().toString() : "Unknown";
                boolean isBanned = cache.isBanned(account.getMinecraftUuid());
                boolean isMuted = cache.isMuted(account.getMinecraftUuid());

                String status;
                if (isBanned && isMuted) {
                    status = localeManager.getMessage("print.alts.status_banned_and_muted");
                } else if (isBanned) {
                    status = localeManager.getMessage("print.alts.status_banned");
                } else if (isMuted) {
                    status = localeManager.getMessage("print.alts.status_muted");
                } else {
                    status = localeManager.getMessage("print.alts.status_clean");
                }

                String color = isBanned ? "&c" : (isMuted ? "&e" : "&a");

                sender.sendMessage(localeManager.getMessage("print.alts.entry", Map.of(
                        "ordinal", String.valueOf(ordinal),
                        "color", color,
                        "username", username,
                        "uuid", uuid,
                        "status", status
                )));
                ordinal++;
            }
            sender.sendMessage(localeManager.getMessage("print.alts.total", Map.of(
                    "count", String.valueOf(linkedAccounts.size())
            )));
        }

        sender.sendMessage(localeManager.getMessage("print.alts.footer"));
    }

    private void handleException(CommandIssuer sender, Throwable throwable, String playerQuery) {
        if (throwable.getCause() instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
        }
    }
}
