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
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.ReportsMenu;
import gg.modl.minecraft.core.impl.menus.staff.StaffReportsMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class ReportsCommand extends BaseCommand {
    private static final String STATUS_OPEN_COLOR = "&a";
    private static final String STATUS_CLOSED_COLOR = "&7";
    private static final String STATUS_DEFAULT_COLOR = "&f";
    private static final int MAX_CONTENT_LENGTH = 80;
    private static final int TRUNCATED_LENGTH = 77;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    @CommandCompletion("@players")
    @CommandAlias("%cmd_reports")
    @Syntax("[player] [-p]")
    @Description("Open the reports menu (for a player or all reports), or use -p to print to chat")
    @Conditions("player|staff")
    public void reports(CommandIssuer sender, @Optional @Name("player") String playerQuery, @Default("") String flags) {
        boolean printMode;
        String actualPlayerQuery;

        if ("-p".equalsIgnoreCase(playerQuery) || "print".equalsIgnoreCase(playerQuery)) {
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

        if (!sender.isPlayer()) {
            if (actualPlayerQuery != null && !actualPlayerQuery.isEmpty())
                printPlayerReports(sender, actualPlayerQuery);
            else sender.sendMessage(localeManager.getMessage("general.invalid_syntax"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        if (actualPlayerQuery == null || actualPlayerQuery.isEmpty()) {
            if (printMode) {
                sender.sendMessage(localeManager.getMessage("general.invalid_syntax"));
                return;
            }
            openStaffReportsMenu(sender, senderUuid);
            return;
        }

        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", actualPlayerQuery)));
        PlayerLookupRequest request = new PlayerLookupRequest(actualPlayerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200 && profileResponse.getProfile() != null) {
                        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
                        ReportsMenu menu = new ReportsMenu(
                            platform, httpClientHolder.getClient(), senderUuid, senderName,
                            profileResponse.getProfile(), null
                        );
                        CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                        menu.display(player);
                    } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
                }).exceptionally(throwable -> {
                    handleException(sender, throwable, actualPlayerQuery);
                    return null;
                });
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            handleException(sender, throwable, actualPlayerQuery);
            return null;
        });
    }

    private void printPlayerReports(CommandIssuer sender, String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getPlayerReports(targetUuid, "all").thenAccept(reportsResponse -> {
                    if (reportsResponse.isSuccess()) displayReports(sender, playerName, reportsResponse.getReports());
                    else displayReports(sender, playerName, List.of());
                }).exceptionally(throwable -> {
                    if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                    else displayReports(sender, playerName, List.of());
                    return null;
                });
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            handleException(sender, throwable, playerQuery);
            return null;
        });
    }

    private void displayReports(CommandIssuer sender, String playerName, List<ReportsResponse.Report> reports) {
        sender.sendMessage(localeManager.getMessage("print.reports.header", Map.of("player", playerName)));

        if (reports == null || reports.isEmpty()) sender.sendMessage(localeManager.getMessage("print.reports.empty"));
        else {
            int ordinal = 1;
            for (ReportsResponse.Report report : reports) {
                String date = report.getCreatedAt() != null ? DateFormatter.format(report.getCreatedAt()) : Constants.UNKNOWN;
                String id = report.getId() != null ? report.getId() : "?";
                String status = report.getStatus() != null ? report.getStatus() : Constants.UNKNOWN;
                String type = report.getType() != null ? report.getType() : Constants.UNKNOWN;
                if ("player".equalsIgnoreCase(type)) type = "gameplay";
                String reporter = report.getReporterName() != null ? report.getReporterName() : Constants.UNKNOWN;

                String coloredStatus = switch (status.toLowerCase()) {
                    case "open" -> STATUS_OPEN_COLOR + "Open";
                    case "closed" -> STATUS_CLOSED_COLOR + "Closed";
                    default -> STATUS_DEFAULT_COLOR + status;
                };

                sender.sendMessage(localeManager.getMessage("print.reports.entry", Map.of(
                        "ordinal", String.valueOf(ordinal),
                        "date", date,
                        "id", id,
                        "status", coloredStatus,
                        "type", type,
                        "reporter", reporter
                )));

                String content = report.getSubject() != null ? report.getSubject() : (report.getContent() != null ? report.getContent() : null);
                if (content != null && !content.isEmpty()) {
                    if (content.length() > MAX_CONTENT_LENGTH) content = content.substring(0, TRUNCATED_LENGTH) + "...";
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
        boolean isAdmin = cache.hasPermission(senderUuid, Permissions.ADMIN);
        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);

        StaffReportsMenu menu = new StaffReportsMenu(
            platform, httpClientHolder.getClient(), senderUuid, senderName,
            isAdmin, panelUrl, null
        );
        CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
        menu.display(player);
    }

    private void handleException(CommandIssuer sender, Throwable throwable, String playerQuery) {
        if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        else sender.sendMessage(localeManager.getMessage("player_lookup.error", Map.of("error", localeManager.sanitizeErrorMessage(throwable.getMessage()))));
    }
}
