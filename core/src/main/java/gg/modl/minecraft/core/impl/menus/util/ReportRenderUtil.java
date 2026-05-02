package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class ReportRenderUtil {

    private ReportRenderUtil() {}

    public static final class LinkableReport {
        private final String id;
        private final String type;
        private final String reporterName;
        private final String content;
        private final Date date;

        public LinkableReport(String id, String type, String reporterName, String content, Date date) {
            this.id = id;
            this.type = type;
            this.reporterName = reporterName;
            this.content = content;
            this.date = date;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getReporterName() {
            return reporterName;
        }

        public String getContent() {
            return content;
        }

        public Date getDate() {
            return date;
        }
    }

    public static List<LinkableReport> loadLinkableReports(ModlHttpClient httpClient, UUID targetUuid) {
        List<LinkableReport> reports = new ArrayList<>();
        try {
            httpClient.getPlayerReports(targetUuid, "Open").thenAccept(response -> {
                if (response.isSuccess() && response.getReports() != null) {
                    for (ReportsResponse.Report report : response.getReports()) {
                        reports.add(new LinkableReport(
                                report.getId(),
                                normalizeReportType(report),
                                report.getReporterName(),
                                getReportContent(report),
                                report.getCreatedAt()
                        ));
                    }
                }
            }).join();
        } catch (Exception ignored) {
        }
        return reports;
    }

    public static Collection<LinkableReport> elementsOrEmptyReports(List<LinkableReport> reports, String currentFilter) {
        if (reports.isEmpty()) return Collections.singletonList(new LinkableReport(null, null, null, null, null));

        List<LinkableReport> filtered = filterLinkableReports(reports, currentFilter);
        if (filtered.isEmpty()) return Collections.singletonList(new LinkableReport(null, null, null, null, null));

        return filtered;
    }

    public static List<LinkableReport> filterLinkableReports(List<LinkableReport> reports, String currentFilter) {
        if ("all".equals(currentFilter)) return reports;

        List<LinkableReport> filtered = new ArrayList<>();
        for (LinkableReport report : reports)
            if (report.getType() != null && report.getType().equalsIgnoreCase(currentFilter))
                filtered.add(report);
        return filtered;
    }

    public static CirrusItem mapLinkableReport(LinkableReport report, Set<String> selectedReportIds,
                                               LocaleManager locale) {
        boolean selected = selectedReportIds.contains(report.getId());

        Map<String, String> vars = new HashMap<>();
        vars.put("id", report.getId());
        vars.put("type", report.getType() != null ? report.getType() : "Unknown");
        vars.put("date", report.getDate() != null ? MenuItems.formatDate(report.getDate()) : "Unknown");
        vars.put("reporter", report.getReporterName() != null ? report.getReporterName() : "Unknown");
        vars.put("content", String.join("\n", processContent(report.getContent())));

        String localeKey = selected ? "menus.link_report_item_selected" : "menus.link_report_item_unselected";
        List<String> lore = buildLore(locale, localeKey + ".lore", vars);
        String title = locale.getMessage(localeKey + ".title", vars);
        CirrusItemType itemType = selected ? CirrusItemType.LIME_DYE : getReportItemType(report.getType());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        ).actionHandler("toggleReport_" + report.getId());
    }

    public static void toggleReportSelection(Set<String> selectedReportIds, String reportId) {
        if (selectedReportIds.contains(reportId))
            selectedReportIds.remove(reportId);
        else
            selectedReportIds.add(reportId);
    }

    public static void toggleFilteredReportSelection(Set<String> selectedReportIds, List<LinkableReport> filtered) {
        boolean allSelected = true;
        for (LinkableReport report : filtered) {
            if (report.getId() != null && !selectedReportIds.contains(report.getId())) {
                allSelected = false;
                break;
            }
        }

        for (LinkableReport report : filtered) {
            if (report.getId() == null) continue;

            if (allSelected)
                selectedReportIds.remove(report.getId());
            else
                selectedReportIds.add(report.getId());
        }
    }

    public static CirrusItemType getReportItemType(String type) {
        if (type == null) return CirrusItemType.PAPER;
        String lower = type.toLowerCase();
        if ("gameplay".equals(lower)) return CirrusItemType.DIAMOND_SWORD;
        if ("chat".equals(lower)) return CirrusItemType.WRITABLE_BOOK;
        return CirrusItemType.PAPER;
    }

    public static String normalizeReportType(ReportsResponse.Report report) {
        String type = report.getType() != null ? report.getType() : report.getCategory();
        if ("player".equalsIgnoreCase(type)) type = "gameplay";
        return type;
    }

    public static String getReportContent(ReportsResponse.Report report) {
        return report.getContent() != null ? report.getContent() : report.getSubject();
    }

    public static UUID parseReportedPlayerUuid(ReportsResponse.Report report) {
        try {
            if (report.getReportedPlayerUuid() != null) {
                return UUID.fromString(report.getReportedPlayerUuid());
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static List<String> processContent(String content) {
        if (content == null || content.isEmpty()) return listOf();

        content = StringUtil.unescapeNewlines(content);
        content = content.replace("**", "").replace("```", "");

        List<String> wrapped = new ArrayList<>();
        for (String paragraph : content.split("\n"))
            if (paragraph.trim().isEmpty())
                wrapped.add("");
            else
                wrapped.addAll(MenuItems.wrapText(paragraph.trim(), 7));
        return wrapped;
    }

    public static List<String> buildLore(LocaleManager locale, String localeKey, Map<String, String> vars) {
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList(localeKey)) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (processed.contains("\n"))
                lore.addAll(Arrays.asList(processed.split("\n")));
            else if (!processed.isEmpty())
                lore.add(processed);
        }
        return lore;
    }

    public static CirrusItem buildTargetHead(LocaleManager locale, Platform platform, AbstractPlayer target, String localeKey) {
        List<String> skullLines = locale.getMessageList(localeKey, mapOf("player", target.getUsername()));
        CirrusItem head = MenuItems.playerHead(
                skullLines.get(0),
                skullLines.subList(1, skullLines.size())
        );
        if (platform.getCache() != null) {
            String texture = platform.getCache().getSkinTexture(target.getUuid());
            if (texture != null) head = head.texture(texture);
        }
        return head;
    }

    public static String getPlayerName(Account account) {
        if (!account.getUsernames().isEmpty()) {
            Account.Username latestDated = null;
            String fallbackName = null;

            for (Account.Username username : account.getUsernames()) {
                if (username == null || username.getUsername() == null || username.getUsername().isEmpty()) {
                    continue;
                }

                fallbackName = username.getUsername();
                if (username.getDate() == null) {
                    continue;
                }

                if (latestDated == null || username.getDate().after(latestDated.getDate())) {
                    latestDated = username;
                }
            }

            if (latestDated != null) {
                return latestDated.getUsername();
            }
            if (fallbackName != null) {
                return fallbackName;
            }
        }
        return "Unknown";
    }
}
