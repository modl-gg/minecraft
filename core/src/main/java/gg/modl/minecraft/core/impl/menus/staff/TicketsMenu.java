package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

        // TODO: Fetch tickets when endpoint GET /v1/panel/tickets is available
        // For now, list is empty
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add filter button
        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions)
                .actionHandler("filter"));

        return items;
    }

    @Override
    protected Collection<Ticket> elements() {
        // Filter and sort tickets (newest first)
        List<Ticket> filtered = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (currentFilter.equals("all") || ticket.getStatus().equalsIgnoreCase(currentFilter)) {
                filtered.add(ticket);
            }
        }

        filtered.sort((t1, t2) -> t2.getCreated().compareTo(t1.getCreated()));
        return filtered;
    }

    @Override
    protected CirrusItem map(Ticket ticket) {
        List<String> lore = new ArrayList<>();

        lore.add(MenuItems.COLOR_GRAY + "Player: " + MenuItems.COLOR_WHITE + ticket.getPlayerName());
        lore.add(MenuItems.COLOR_GRAY + "Title: " + MenuItems.COLOR_WHITE + ticket.getTitle());
        lore.add(MenuItems.COLOR_GRAY + "Created: " + MenuItems.COLOR_WHITE + MenuItems.formatDate(ticket.getCreated()));
        lore.add(MenuItems.COLOR_GRAY + "Status: " + getStatusColor(ticket.getStatus()) + ticket.getStatus());
        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Click to open in panel");

        // Use book if no staff response, writable book if has response
        ItemType itemType = ticket.hasStaffResponse() ? ItemType.WRITABLE_BOOK : ItemType.BOOK;

        return CirrusItem.of(
                itemType,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Ticket #" + ticket.getId()),
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

        // Override header navigation
        registerActionHandler("openOnlinePlayers", click -> {
            click.clickedMenu().close();
            new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
        registerActionHandler("openReports", click -> {
            click.clickedMenu().close();
            new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
        registerActionHandler("openPunishments", click -> {
            click.clickedMenu().close();
            new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
        registerActionHandler("openTickets", click -> {
            // Already here, do nothing
        });
        registerActionHandler("openPanel", click -> {
            sendMessage("");
            sendMessage(MenuItems.COLOR_GOLD + "Staff Panel:");
            sendMessage(MenuItems.COLOR_AQUA + panelUrl);
            sendMessage("");
        });
        registerActionHandler("openSettings", click -> {
            click.clickedMenu().close();
            new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
    }

    private void handleFilter(Click click) {
        // Cycle through filter options
        int currentIndex = filterOptions.indexOf(currentFilter);
        int nextIndex = (currentIndex + 1) % filterOptions.size();
        currentFilter = filterOptions.get(nextIndex);

        // Refresh menu
        click.clickedMenu().close();
        new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                .display(click.player());
    }
}
