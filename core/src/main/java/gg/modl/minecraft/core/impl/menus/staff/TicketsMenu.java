package gg.modl.minecraft.core.impl.menus.staff;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.MenuAsync;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.ClickableJsonMessage;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TicketsMenu extends BaseStaffListMenu<TicketsMenu.Ticket> {
    @Getter
    public static class Ticket {
        private final String id, playerName, title;
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
    }

    private final List<Ticket> tickets = new ArrayList<>();
    private String currentStatusFilter = "open";
    @Getter private CompletableFuture<Void> dataFuture;

    public TicketsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Support Tickets", platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction);
        activeTab = StaffTab.TICKETS;

        this.dataFuture = fetchTickets();
    }

    private CompletableFuture<Void> fetchTickets() {
        return httpClient.getTickets(null, null).thenAccept(response -> {
            if (response.isSuccess() && response.getTickets() != null) {
                tickets.clear();
                for (TicketsResponse.Ticket ticket : response.getTickets()) {
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
        }).exceptionally(e -> null);
    }

    public TicketsMenu withStatusFilter(String statusFilter) {
        this.currentStatusFilter = statusFilter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        items.put(MenuSlots.FILTER_BUTTON, MenuItems.statusToggleButton(currentStatusFilter, "tickets"));

        return items;
    }

    @Override
    protected Collection<Ticket> elements() {
        if (tickets.isEmpty())
            return Collections.singletonList(new Ticket(null, null, null, null, null, false));

        List<Ticket> filtered = new ArrayList<>();

        for (Ticket ticket : tickets) {
            if (ReportRenderUtil.matchesStatusFilter(currentStatusFilter, ticket.getStatus()))
                filtered.add(ticket);
        }

        if (filtered.isEmpty())
            return Collections.singletonList(new Ticket(null, null, null, null, null, false));

        filtered.sort((t1, t2) -> {
            if (t1.getCreated() == null && t2.getCreated() == null) return 0;
            if (t1.getCreated() == null) return 1;
            if (t2.getCreated() == null) return -1;
            return t2.getCreated().compareTo(t1.getCreated());
        });
        return filtered;
    }

    @Override
    protected CirrusItem map(Ticket ticket) {
        LocaleManager locale = PluginServices.locale();

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

        CirrusItemType itemType = ticket.isHasStaffResponse() ? CirrusItemType.WRITABLE_BOOK : CirrusItemType.BOOK;

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private String getStatusColor(String status) {
        if (status == null) return MenuItems.COLOR_GRAY;
        String lower = status.toLowerCase();
        if ("open".equals(lower)) return MenuItems.COLOR_RED;
        if ("unfinished".equals(lower)) return MenuItems.COLOR_YELLOW;
        if ("closed".equals(lower)) return MenuItems.COLOR_GREEN;
        return MenuItems.COLOR_GRAY;
    }

    @Override
    protected void handleClick(Click click, Ticket ticket) {
        if (ticket.getId() == null) return;

        click.clickedMenu().close();

        String ticketUrl = panelUrl + "/ticket/" + ticket.getId();
        String json = ClickableJsonMessage.empty()
                .extra(ClickableJsonMessage.text("Ticket #" + ticket.getId() + ": ").color("gold"))
                .extra(ClickableJsonMessage.text(ticketUrl)
                        .color("aqua")
                        .underlined(true)
                        .openUrl(ticketUrl)
                        .hoverText("Click to open in browser"))
                .toJson();
        platform.sendJsonMessage(viewerUuid, json);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("filter", this::handleFilter);

        registerActionHandler("openTickets", click -> {});
    }

    private void handleFilter(Click click) {
        String newStatus = "open".equalsIgnoreCase(currentStatusFilter) ? "closed" : "open";
        TicketsMenu menu = new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                .withStatusFilter(newStatus);
        MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
    }
}
