package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.ReportsMenu;
import gg.modl.minecraft.core.impl.menus.staff.StaffReportsMenu;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Command to open the Reports Menu GUI.
 * With a player argument: opens the inspect Reports menu for that player
 * Without a player argument: opens the Staff Reports menu
 * Use -p flag to print reports to chat instead of opening GUI
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
    @Syntax("[player] [-p]")
    @Description("Open the reports menu (for a player or all reports), or use -p to print to chat")
    @Conditions("player|staff")
    public void reports(CommandIssuer sender, @Optional @Name("player") String playerQuery, @Default("") String flags) {
        // Handle case where playerQuery is actually the -p flag (no player specified)
        boolean printMode;
        String actualPlayerQuery;

        if ("-p".equalsIgnoreCase(playerQuery) || "print".equalsIgnoreCase(playerQuery)) {
            // No player specified, -p was the first arg
            printMode = true;
            actualPlayerQuery = null;
        } else {
            printMode = flags.equalsIgnoreCase("-p") || flags.equalsIgnoreCase("print");
            actualPlayerQuery = playerQuery;
        }

        if (printMode && actualPlayerQuery != null && !actualPlayerQuery.isEmpty()) {
            printPlayerReports(sender, actualPlayerQuery);
            return;
        }

        // Console always uses print mode
        if (!sender.isPlayer()) {
            if (actualPlayerQuery != null && !actualPlayerQuery.isEmpty()) {
                printPlayerReports(sender, actualPlayerQuery);
            } else {
                sender.sendMessage(localeManager.getMessage("general.invalid_syntax"));
            }
            return;
        }

        // Non-print mode requires V2 API for GUI
        if (!printMode && httpClientHolder.getApiVersion() == ApiVersion.V1) {
            sender.sendMessage(localeManager.getMessage("api_errors.menus_require_v2"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        // If no player specified, open staff reports menu
        if (actualPlayerQuery == null || actualPlayerQuery.isEmpty()) {
            if (printMode) {
                // -p without player not supported, inform user
                sender.sendMessage(localeManager.getMessage("general.invalid_syntax"));
                return;
            }
            openStaffReportsMenu(sender, senderUuid);
            return;
        }

        // Player specified, open inspect reports menu
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", actualPlayerQuery)));

        // Look up the player
        PlayerLookupRequest request = new PlayerLookupRequest(actualPlayerQuery);

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
                    handleException(sender, throwable, actualPlayerQuery);
                    return null;
                });
            } else {
                sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            }
        }).exceptionally(throwable -> {
            handleException(sender, throwable, actualPlayerQuery);
            return null;
        });
    }

    private void printPlayerReports(CommandIssuer sender, String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                getHttpClient().getPlayerReports(targetUuid, "all").thenAccept(reportsResponse -> {
                    if (reportsResponse.isSuccess()) {
                        displayReports(sender, playerName, reportsResponse.getReports());
                    } else {
                        displayReports(sender, playerName, List.of());
                    }
                }).exceptionally(throwable -> {
                    if (throwable.getCause() instanceof PanelUnavailableException) {
                        sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                    } else {
                        displayReports(sender, playerName, List.of());
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

    private void displayReports(CommandIssuer sender, String playerName, List<ReportsResponse.Report> reports) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

        sender.sendMessage(localeManager.getMessage("print.reports.header", Map.of("player", playerName)));

        if (reports == null || reports.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("print.reports.empty"));
        } else {
            int ordinal = 1;
            for (ReportsResponse.Report report : reports) {
                String date = report.getCreatedAt() != null ? dateFormat.format(report.getCreatedAt()) : "Unknown";
                String id = report.getId() != null ? report.getId() : "?";
                String status = report.getStatus() != null ? report.getStatus() : "Unknown";
                String type = report.getType() != null ? report.getType() : "Unknown";
                String reporter = report.getReporterName() != null ? report.getReporterName() : "Unknown";

                // Color the status
                String coloredStatus;
                switch (status.toLowerCase()) {
                    case "open":
                        coloredStatus = "&aOpen";
                        break;
                    case "closed":
                    case "resolved":
                        coloredStatus = "&7Closed";
                        break;
                    case "under review":
                        coloredStatus = "&eUnder Review";
                        break;
                    default:
                        coloredStatus = "&f" + status;
                        break;
                }

                sender.sendMessage(localeManager.getMessage("print.reports.entry", Map.of(
                        "ordinal", String.valueOf(ordinal),
                        "date", date,
                        "id", id,
                        "status", coloredStatus,
                        "type", type,
                        "reporter", reporter
                )));

                // Show content if available
                String content = report.getSubject() != null ? report.getSubject() : (report.getContent() != null ? report.getContent() : null);
                if (content != null && !content.isEmpty()) {
                    // Truncate long content
                    if (content.length() > 80) {
                        content = content.substring(0, 77) + "...";
                    }
                    sender.sendMessage(localeManager.getMessage("print.reports.entry_content", Map.of(
                            "content", content
                    )));
                }
                ordinal++;
            }
            sender.sendMessage(localeManager.getMessage("print.reports.total", Map.of(
                    "count", String.valueOf(reports.size())
            )));
        }

        sender.sendMessage(localeManager.getMessage("print.reports.footer"));
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
