package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tickets Menu - displays support tickets.
 */
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
    private final List<String> filterOptions = Arrays.asList("all", "open", "pending", "closed");
    private final String panelUrl;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    /**
     * Create a new tickets menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param backAction Action to return to parent menu
     */
    public TicketsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Support Tickets", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        activeTab = StaffTab.TICKETS;

        // Fetch tickets from API
        fetchTickets();
    }

    private void fetchTickets() {
        httpClient.getTickets(null, null).thenAccept(response -> {
            if (response.isSuccess() && response.getTickets() != null) {
                tickets.clear();
                for (var ticket : response.getTickets()) {
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

    /**
     * Set the current filter (used when cycling filters).
     */
    public TicketsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add filter button at slot 40 (y position in navigation row)
        // Note: actionHandler("filter") is already set in MenuItems.filterButton()
        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions));

        return items;
    }

    @Override
    protected Collection<Ticket> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (tickets.isEmpty()) {
            return Collections.singletonList(new Ticket(null, null, null, null, null, false));
        }

        // Filter and sort tickets (newest first)
        List<Ticket> filtered = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (currentFilter.equals("all") || ticket.getStatus().equalsIgnoreCase(currentFilter)) {
                filtered.add(ticket);
            }
        }

        // If filtering results in empty list, return placeholder
        if (filtered.isEmpty()) {
            return Collections.singletonList(new Ticket(null, null, null, null, null, false));
        }

        filtered.sort((t1, t2) -> t2.getCreated().compareTo(t1.getCreated()));
        return filtered;
    }

    @Override
    protected CirrusItem map(Ticket ticket) {
        LocaleManager locale = platform.getLocaleManager();

        // Handle placeholder for empty list
        if (ticket.getId() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.tickets"));
        }

        // Build variables map
        Map<String, String> vars = new HashMap<>();
        vars.put("id", ticket.getId());
        vars.put("player", ticket.getPlayerName() != null ? ticket.getPlayerName() : "Unknown");
        vars.put("title", ticket.getTitle() != null ? ticket.getTitle() : "No title");
        vars.put("date", MenuItems.formatDate(ticket.getCreated()));
        vars.put("status", getStatusColor(ticket.getStatus()) + ticket.getStatus());

        // Get lore from locale
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.ticket_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            lore.add(processed);
        }

        // Get title from locale
        String title = locale.getMessage("menus.ticket_item.title", vars);

        // Use book if no staff response, writable book if has response
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
            case "pending":
                return MenuItems.COLOR_YELLOW;
            case "closed":
            case "resolved":
                return MenuItems.COLOR_GREEN;
            default:
                return MenuItems.COLOR_GRAY;
        }
    }

    @Override
    protected void handleClick(Click click, Ticket ticket) {
        // Handle placeholder - do nothing
        if (ticket.getId() == null) {
            return;
        }

        // Open ticket link in chat
        String ticketUrl = panelUrl + "/tickets/" + ticket.getId();
        sendMessage("");
        sendMessage(MenuItems.COLOR_GOLD + "Ticket #" + ticket.getId() + ":");
        sendMessage(MenuItems.COLOR_AQUA + ticketUrl);
        sendMessage("");
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Filter handler
        registerActionHandler("filter", this::handleFilter);

        // Override header navigation - primary tabs should NOT pass backAction
        registerActionHandler("openOnlinePlayers", ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPunishments", ActionHandlers.openMenu(
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openTickets", click -> {
            // Already here, do nothing
        });

        registerActionHandler("openPanel", click -> {
            sendMessage("");
            sendMessage(MenuItems.COLOR_GOLD + "Staff Panel:");
            sendMessage(MenuItems.COLOR_AQUA + panelUrl);
            sendMessage("");
        });

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));
    }

    private void handleFilter(Click click) {
        // Cycle through filter options
        int currentIndex = filterOptions.indexOf(currentFilter);
        int nextIndex = (currentIndex + 1) % filterOptions.size();
        String newFilter = filterOptions.get(nextIndex);

        // Refresh menu with new filter - preserve backAction if present
        ActionHandlers.openMenu(
                new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                        .withFilter(newFilter))
                .handle(click);
    }
}
