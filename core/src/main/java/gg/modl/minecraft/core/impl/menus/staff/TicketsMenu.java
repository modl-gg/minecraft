package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class TicketsMenu extends BaseStaffListMenu<TicketsMenu.Ticket> {
    // Placeholder Ticket class since no endpoint exists yet
    public static class Ticket {
        private final String id;
        private final String playerName;
        private final String title;
        private final Date created;
        private final String status;
        private final boolean hasStaffResponse;

        public Ticket(String id, String playerName, String title, Date created, String status, boolean hasStaffResponse) {
            this.id = id;
            this.playerName = playerName;
            this.title = title;
            this.created = created;
            this.status = status;
            this.hasStaffResponse = hasStaffResponse;
        }

        public String getId() { return id; }
        public String getPlayerName() { return playerName; }
        public String getTitle() { return title; }
        public Date getCreated() { return created; }
        public String getStatus() { return status; }
        public boolean hasStaffResponse() { return hasStaffResponse; }
    }

    private List<Ticket> tickets = new ArrayList<>();
    private String currentFilter = "all";
    private String currentStatusFilter = "open";
    private final List<String> filterOptions = Arrays.asList("all", "open", "unfinished", "closed");
    private final String panelUrl;

    public TicketsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Support Tickets", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        activeTab = StaffTab.TICKETS;

        fetchTickets();
    }

    private void fetchTickets() {
        httpClient.getTickets(null, null).thenAccept(response -> {
            if (response.isSuccess() && response.getTickets() != null) {
                tickets.clear();
                for (var ticket : response.getTickets()) {
                    if ("Unfinished".equalsIgnoreCase(ticket.getStatus())) {
                        continue;
                    }
                    tickets.add(new Ticket(
                            ticket.getId(),
                            ticket.getPlayerName(),
                            ticket.getSubject(),
                            ticket.getCreatedAt(),
                            ticket.getStatus(),
                            ticket.isHasStaffResponse()
                    ));
                }
            }
        }).exceptionally(e -> {
            // Failed to fetch - list remains empty
            return null;
        });
    }

    public TicketsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    public TicketsMenu withStatusFilter(String statusFilter) {
        this.currentStatusFilter = statusFilter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions, currentStatusFilter, "tickets"));

        return items;
    }

    @Override
    protected Collection<Ticket> elements() {
        if (tickets.isEmpty())
            return Collections.singletonList(new Ticket(null, null, null, null, null, false));

        List<Ticket> filtered = new ArrayList<>();

        for (Ticket ticket : tickets) {
            boolean typeMatch = currentFilter.equals("all") || (ticket.getStatus() != null && ticket.getStatus().equalsIgnoreCase(currentFilter));
            boolean statusMatch = "open".equalsIgnoreCase(currentStatusFilter)
                    ? !"closed".equalsIgnoreCase(ticket.getStatus())
                    : "closed".equalsIgnoreCase(ticket.getStatus());
            if (typeMatch && statusMatch)
                filtered.add(ticket);
        }

        if (filtered.isEmpty())
            return Collections.singletonList(new Ticket(null, null, null, null, null, false));

        filtered.sort((t1, t2) -> t2.getCreated().compareTo(t1.getCreated()));
        return filtered;
    }

    @Override
    protected CirrusItem map(Ticket ticket) {
        LocaleManager locale = platform.getLocaleManager();

        if (ticket.getId() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.tickets"));

        Map<String, String> vars = new HashMap<>();
        vars.put("id", ticket.getId());
        vars.put("player", ticket.getPlayerName() != null ? ticket.getPlayerName() : "Unknown");
        vars.put("title", ticket.getTitle() != null ? ticket.getTitle() : "No title");
        vars.put("date", MenuItems.formatDate(ticket.getCreated()));
        vars.put("status", getStatusColor(ticket.getStatus()) + ticket.getStatus());

        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.ticket_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            lore.add(processed);
        }

        String title = locale.getMessage("menus.ticket_item.title", vars);

        CirrusItemType itemType = ticket.hasStaffResponse() ? CirrusItemType.WRITABLE_BOOK : CirrusItemType.BOOK;

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private String getStatusColor(String status) {
        if (status == null) return MenuItems.COLOR_GRAY;
        switch (status.toLowerCase()) {
            case "open":
                return MenuItems.COLOR_RED;
            case "unfinished":
                return MenuItems.COLOR_YELLOW;
            case "closed":
                return MenuItems.COLOR_GREEN;
            default:
                return MenuItems.COLOR_GRAY;
        }
    }

    @Override
    protected void handleClick(Click click, Ticket ticket) {
        if (ticket.getId() == null) return;

        click.clickedMenu().close();

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
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("filter", this::handleFilter);

        StaffNavigationHandlers.registerAll(
                (name, handler) -> registerActionHandler(name, handler),
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("openTickets", click -> {});
    }

    private void handleFilter(Click click) {
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            String newStatus = "open".equalsIgnoreCase(currentStatusFilter) ? "closed" : "open";
            ActionHandlers.openMenu(
                    new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                            .withFilter(currentFilter)
                            .withStatusFilter(newStatus))
                    .handle(click);
        } else {
            int currentIndex = filterOptions.indexOf(currentFilter);
            int nextIndex = (currentIndex + 1) % filterOptions.size();
            String newFilter = filterOptions.get(nextIndex);

            ActionHandlers.openMenu(
                    new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                            .withFilter(newFilter)
                            .withStatusFilter(currentStatusFilter))
                    .handle(click);
        }
    }
}
