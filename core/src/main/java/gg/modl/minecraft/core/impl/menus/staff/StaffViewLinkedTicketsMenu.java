package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.LinkedTicketItems;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class StaffViewLinkedTicketsMenu extends BaseStaffListMenu<TicketsResponse.Ticket> {
    private final List<TicketsResponse.Ticket> tickets;
    private final String panelUrl;

    public StaffViewLinkedTicketsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                      boolean isAdmin, String panelUrl, List<String> ticketIds,
                                      Consumer<CirrusPlayerWrapper> backAction) {
        super("Linked Tickets", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;

        activeTab = StaffTab.PUNISHMENTS;

        tickets = LinkedTicketItems.loadTickets(httpClient, ticketIds);
    }

    @Override
    protected Collection<TicketsResponse.Ticket> elements() {
        return LinkedTicketItems.elementsOrEmpty(tickets);
    }

    @Override
    protected CirrusItem map(TicketsResponse.Ticket ticket) {
        if (ticket.getId() == null) return createEmptyPlaceholder("No linked tickets");
        return LinkedTicketItems.mapTicket(ticket, platform);
    }

    @Override
    protected void handleClick(Click click, TicketsResponse.Ticket ticket) {
        LinkedTicketItems.handleTicketClick(click, ticket, platform, viewerUuid);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);
    }
}
