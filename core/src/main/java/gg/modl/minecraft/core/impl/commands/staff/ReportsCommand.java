package gg.modl.minecraft.core.impl.commands.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.inspect.ReportsMenu;
import gg.modl.minecraft.core.impl.menus.staff.StaffReportsMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.Pagination;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.command.CommandActor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class ReportsCommand {
    private static final String STATUS_OPEN_COLOR = "&a", STATUS_CLOSED_COLOR = "&7", STATUS_DEFAULT_COLOR = "&f";
    private static final int MAX_CONTENT_LENGTH = 80, TRUNCATED_LENGTH = 77;

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    @Command("reports")
    @Description("Open the reports menu (for a player or all reports), or use -p to print to chat")
    @PlayerOnly @StaffOnly
    public void reports(CommandActor actor, @revxrsal.commands.annotation.Optional @Named("player") String playerQuery, @revxrsal.commands.annotation.Optional String flags) {
        if (flags == null) flags = "";
        boolean printMode;
        String actualPlayerQuery;

        int page;
        if ("-p".equalsIgnoreCase(playerQuery) || "print".equalsIgnoreCase(playerQuery)) {
            printMode = true;
            actualPlayerQuery = null;
            page = 1;
        } else {
            page = Pagination.parsePrintFlags(flags);
            printMode = page > 0;
            actualPlayerQuery = playerQuery;
        }

        if (printMode && actualPlayerQuery != null && !actualPlayerQuery.isEmpty()) {
            printPlayerReports(actor, actualPlayerQuery, Math.max(1, page));
            return;
        }

        if (actor.uniqueId() == null) {
            if (actualPlayerQuery != null && !actualPlayerQuery.isEmpty()) {
                printPlayerReports(actor, actualPlayerQuery, Math.max(1, page));
            } else actor.reply(localeManager.getMessage("general.invalid_syntax"));

            return;
        }

        UUID senderUuid = actor.uniqueId();

        if (actualPlayerQuery == null || actualPlayerQuery.isEmpty()) {
            if (printMode) {
                actor.reply(localeManager.getMessage("general.invalid_syntax"));
                return;
            }
            openStaffReportsMenu(senderUuid);
            return;
        }

        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", actualPlayerQuery)));
        PlayerLookupRequest request = new PlayerLookupRequest(actualPlayerQuery);

        httpClientHolder.getClient().lookupPlayerProfile(request).thenAccept(profileResponse -> {
            if (profileResponse.getStatus() == 200) {
                String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
                ReportsMenu menu = new ReportsMenu(
                    platform, httpClientHolder.getClient(), senderUuid, senderName,
                    profileResponse.getProfile(), null
                );
                CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                menu.display(player);
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private void printPlayerReports(CommandActor actor, String playerQuery, int page) {
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getPlayerReports(targetUuid, "all").thenAccept(reportsResponse -> {
                    if (reportsResponse.isSuccess()) {
                        displayReports(actor, playerName, reportsResponse.getReports(), page);
                    } else displayReports(actor, playerName, listOf(), page);
                }).exceptionally(throwable -> {
                    if (throwable.getCause() instanceof PanelUnavailableException) {
                        actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
                    } else displayReports(actor, playerName, listOf(), page);

                    return null;
                });
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private static final int ENTRIES_PER_PAGE = 8;

    private void displayReports(CommandActor actor, String playerName, List<ReportsResponse.Report> reports, int page) {
        actor.reply(localeManager.getMessage("print.reports.header", mapOf("player", playerName)));

        if (reports == null || reports.isEmpty()) actor.reply(localeManager.getMessage("print.reports.empty"));
        else {
            Pagination.Page pg = Pagination.paginate(reports, ENTRIES_PER_PAGE, page);
            for (int i = pg.getStart(); i < pg.getEnd(); i++) {
                int ordinal = i + 1;
                ReportsResponse.Report report = reports.get(i);
                String date = report.getCreatedAt() != null ? DateFormatter.format(report.getCreatedAt()) : Constants.UNKNOWN;
                String id = report.getId() != null ? report.getId() : "?";
                String status = report.getStatus() != null ? report.getStatus() : Constants.UNKNOWN;
                String type = report.getType() != null ? report.getType() : Constants.UNKNOWN;
                if ("player".equalsIgnoreCase(type)) type = "gameplay";
                String reporter = report.getReporterName() != null ? report.getReporterName() : Constants.UNKNOWN;

                String statusLower = status.toLowerCase();
                String coloredStatus;
                if ("open".equals(statusLower)) {
                    coloredStatus = STATUS_OPEN_COLOR + "Open";
                } else if ("closed".equals(statusLower)) {
                    coloredStatus = STATUS_CLOSED_COLOR + "Closed";
                } else {
                    coloredStatus = STATUS_DEFAULT_COLOR + status;
                }

                actor.reply(localeManager.getMessage("print.reports.entry", mapOf(
                        "ordinal", String.valueOf(ordinal),
                        "date", date,
                        "id", id,
                        "status", coloredStatus,
                        "type", type,
                        "reporter", reporter
                )));

                String content = report.getSubject() != null ? report.getSubject() : (report.getContent() != null ? report.getContent() : null);
                if (content != null && !content.isEmpty()) {
                    if (content.length() > MAX_CONTENT_LENGTH) {
                        content = content.substring(0, TRUNCATED_LENGTH) + "...";
                    }

                    actor.reply(localeManager.getMessage("print.reports.entry_content", mapOf(
                            "content", content
                    )));
                }
            }
            actor.reply(localeManager.getMessage("print.reports.total", mapOf(
                    "count", String.valueOf(reports.size()),
                    "page", String.valueOf(pg.getPage()),
                    "total_pages", String.valueOf(pg.getTotalPages())
            )));
        }

        actor.reply(localeManager.getMessage("print.reports.footer"));
    }

    private void openStaffReportsMenu(UUID senderUuid) {
        boolean isAdmin = cache.hasPermission(senderUuid, Permissions.ADMIN);
        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);

        StaffReportsMenu menu = new StaffReportsMenu(
            platform, httpClientHolder.getClient(), senderUuid, senderName,
            isAdmin, panelUrl, null
        );
        CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
        menu.display(player);
    }

}
