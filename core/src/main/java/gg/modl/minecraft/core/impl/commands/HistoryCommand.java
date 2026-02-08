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
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.HistoryMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PunishmentMessages;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Command to open the History Menu GUI for a player,
 * or print punishment history to chat with the -p flag.
 */
@RequiredArgsConstructor
public class HistoryCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @CommandAlias("history|hist")
    @Syntax("<player> [-p]")
    @Description("Open the punishment history menu for a player, or use -p to print to chat")
    @Conditions("player|staff")
    public void history(CommandIssuer sender, @Name("player") String playerQuery, @Default("") String flags) {
        boolean printMode = flags.equalsIgnoreCase("-p") || flags.equalsIgnoreCase("print");

        // Console can't open GUI - auto-use print mode with -p, otherwise reject
        if (!sender.isPlayer()) {
            if (printMode) {
                printHistory(sender, playerQuery);
            } else {
                sender.sendMessage(localeManager.getMessage("general.gui_requires_player"));
            }
            return;
        }

        if (printMode) {
            printHistory(sender, playerQuery);
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

                // Fetch full profile for the history menu
                getHttpClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        platform.runOnMainThread(() -> {
                            // Get sender name
                            String senderName = "Staff";
                            if (platform.getPlayer(senderUuid) != null) {
                                senderName = platform.getPlayer(senderUuid).username();
                            }

                            // Open the history menu
                            HistoryMenu menu = new HistoryMenu(
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

    private void printHistory(CommandIssuer sender, String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                getHttpClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        Account profile = profileResponse.getProfile();
                        displayHistory(sender, playerName, profile);
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

    private void displayHistory(CommandIssuer sender, String playerName, Account profile) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

        sender.sendMessage(localeManager.getMessage("print.history.header", Map.of("player", playerName)));

        if (profile.getPunishments().isEmpty()) {
            sender.sendMessage(localeManager.getMessage("print.history.empty"));
        } else {
            int ordinal = 1;
            for (Punishment punishment : profile.getPunishments()) {
                String type = punishment.getTypeCategory();
                String id = punishment.getId();
                String issuer = punishment.getIssuerName();
                String date = dateFormat.format(punishment.getIssued());
                String ordinalStr = String.valueOf(ordinal);

                if (punishment.isActive()) {
                    Date expires = punishment.getExpires();
                    if (expires == null) {
                        // Permanent
                        sender.sendMessage(localeManager.getMessage("print.history.entry_permanent", Map.of(
                                "ordinal", ordinalStr,
                                "type", type,
                                "id", id,
                                "issuer", issuer,
                                "date", date
                        )));
                    } else {
                        // Active with expiry
                        long timeLeft = expires.getTime() - System.currentTimeMillis();
                        String expiresFormatted = PunishmentMessages.formatDuration(timeLeft);
                        sender.sendMessage(localeManager.getMessage("print.history.entry_active", Map.of(
                                "ordinal", ordinalStr,
                                "type", type,
                                "id", id,
                                "issuer", issuer,
                                "date", date,
                                "expiry", expiresFormatted
                        )));
                    }
                } else {
                    // Check if pardoned
                    boolean pardoned = punishment.getModifications().stream()
                            .anyMatch(m -> m.getType() != null &&
                                    (m.getType().name().contains("PARDON") || m.getType().name().equals("APPEAL_ACCEPT")));
                    if (pardoned) {
                        sender.sendMessage(localeManager.getMessage("print.history.entry_pardoned", Map.of(
                                "ordinal", ordinalStr,
                                "type", type,
                                "id", id,
                                "issuer", issuer,
                                "date", date
                        )));
                    } else {
                        sender.sendMessage(localeManager.getMessage("print.history.entry_inactive", Map.of(
                                "ordinal", ordinalStr,
                                "type", type,
                                "id", id,
                                "issuer", issuer,
                                "date", date
                        )));
                    }
                }
                ordinal++;
            }
            sender.sendMessage(localeManager.getMessage("print.history.total", Map.of(
                    "count", String.valueOf(profile.getPunishments().size())
            )));
        }

        sender.sendMessage(localeManager.getMessage("print.history.footer"));
    }

    private void handleException(CommandIssuer sender, Throwable throwable, String playerQuery) {
        if (throwable.getCause() instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
        }
    }
}
