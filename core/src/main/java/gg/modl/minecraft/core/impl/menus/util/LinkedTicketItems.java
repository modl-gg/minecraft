package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LinkedTicketItems {
    private LinkedTicketItems() {}

    public static List<TicketsResponse.Ticket> loadTickets(ModlHttpClient httpClient, List<String> ticketIds) {
        List<TicketsResponse.Ticket> tickets = new ArrayList<>();
        if (ticketIds == null || ticketIds.isEmpty()) return tickets;

        try {
            httpClient.getTicketsByIds(ticketIds).thenAccept(response -> {
                if (response != null && response.isSuccess() && response.getTickets() != null) {
                    tickets.addAll(response.getTickets());
                }
            }).join();
        } catch (Exception ignored) {
        }
        return tickets;
    }

    public static Collection<TicketsResponse.Ticket> elementsOrEmpty(List<TicketsResponse.Ticket> tickets) {
        if (tickets.isEmpty()) return Collections.singletonList(new TicketsResponse.Ticket());
        return tickets;
    }

    public static CirrusItem mapTicket(TicketsResponse.Ticket ticket, Platform platform) {
        LocaleManager locale = platform.getLocaleManager();

        String ticketType = ticket.getCategory() != null ? ticket.getCategory() : (ticket.getType() != null ? ticket.getType() : "Unknown");
        if ("player".equalsIgnoreCase(ticketType)) ticketType = "gameplay";
        String subject = ticket.getSubject() != null ? ticket.getSubject() : "No subject";
        String status = ticket.getStatus() != null ? ticket.getStatus() : "Unknown";
        String formattedDate = ticket.getCreatedAt() != null ? MenuItems.formatDate(ticket.getCreatedAt()) : "Unknown";
        String playerName = ticket.getPlayerName() != null ? ticket.getPlayerName() : "Unknown";

        String statusLower = status.toLowerCase();
        String statusColored;
        if ("open".equals(statusLower)) {
            statusColored = MenuItems.COLOR_GREEN + status;
        } else if ("closed".equals(statusLower)) {
            statusColored = MenuItems.COLOR_RED + status;
        } else if ("unfinished".equals(statusLower)) {
            statusColored = MenuItems.COLOR_YELLOW + status;
        } else {
            statusColored = MenuItems.COLOR_GRAY + status;
        }

        String rawContent = ticket.getFirstReplyContent() != null ? ticket.getFirstReplyContent() : "";
        rawContent = StringUtil.unescapeNewlines(rawContent);
        rawContent = rawContent
                .replace("```", "")
                .replace("**", "")
                .replace("__", "")
                .replace("~~", "")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("(?m)^>\\s?", "");
        List<String> wrappedContent = new ArrayList<>();
        for (String paragraph : rawContent.split("\n"))
            if (paragraph.trim().isEmpty())
                wrappedContent.add("");
            else
                wrappedContent.addAll(MenuItems.wrapText(paragraph.trim(), 7));

        Map<String, String> vars = new HashMap<>();
        vars.put("id", ticket.getId());
        vars.put("type", ticketType);
        vars.put("title", subject);
        vars.put("subject", subject);
        vars.put("status", statusColored);
        vars.put("date", formattedDate);
        vars.put("player", playerName);
        vars.put("content", String.join("\n", wrappedContent));

        String title = locale.getMessage("menus.linked_ticket_item.title", vars);
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.linked_ticket_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (processed.contains("\n"))
                lore.addAll(Arrays.asList(processed.split("\n")));
            else
                lore.add(processed);
        }

        CirrusItemType itemType = getTicketItemType(ticketType);

        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Click to view in panel");

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    public static void handleTicketClick(Click click, TicketsResponse.Ticket ticket, Platform platform, UUID viewerUuid) {
        if (ticket.getId() == null) return;

        click.clickedMenu().close();

        String panelUrl = PunishmentMessages.getPanelUrl();
        if (panelUrl != null && !panelUrl.isEmpty()) {
            String ticketUrl = panelUrl + "/ticket/" + ticket.getId();
            String escapedUrl = ticketUrl.replace("\"", "\\\"");
            String json = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"Ticket #%s: \",\"color\":\"gold\"}," +
                "{\"text\":\"%s\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to open in browser\"}}]}",
                ticket.getId(), escapedUrl, ticketUrl
            );
            platform.sendJsonMessage(viewerUuid, json);
        } else {
            platform.sendMessage(viewerUuid, MenuItems.COLOR_GOLD + "Ticket #" + ticket.getId());
        }
    }

    public static CirrusItemType getTicketItemType(String type) {
        if (type == null) return CirrusItemType.PAPER;
        String lower = type.toLowerCase();
        if ("gameplay".equals(lower)) return CirrusItemType.DIAMOND_SWORD;
        if ("chat".equals(lower)) return CirrusItemType.PAPER;
        if ("support".equals(lower)) return CirrusItemType.BOOK;
        if ("appeal".equals(lower)) return CirrusItemType.GOLDEN_APPLE;
        if ("bug".equals(lower)) return CirrusItemType.WRITABLE_BOOK;
        return CirrusItemType.PAPER;
    }
}
