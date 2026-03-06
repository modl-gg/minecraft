package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.LinkedTicketItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ViewLinkedTicketsMenu extends BaseInspectListMenu<TicketsResponse.Ticket> {
    private final List<TicketsResponse.Ticket> tickets = new ArrayList<>();
    private final Consumer<CirrusPlayerWrapper> rootBackAction;

    public ViewLinkedTicketsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                  Account targetAccount, List<String> ticketIds,
                                  Consumer<CirrusPlayerWrapper> backAction, Consumer<CirrusPlayerWrapper> rootBackAction) {
        super("Linked Tickets", platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.rootBackAction = rootBackAction;

        activeTab = InspectTab.HISTORY;

        if (ticketIds != null && !ticketIds.isEmpty()) {
            try {
                httpClient.getTicketsByIds(ticketIds).thenAccept(response -> {
                    if (response != null && response.isSuccess() && response.getTickets() != null) {
                        tickets.addAll(response.getTickets());
                    }
                }).join();
            } catch (Exception e) {
                // Failed to fetch - list remains empty
            }
        }
    }

    @Override
    protected Collection<TicketsResponse.Ticket> elements() {
        if (tickets.isEmpty())
            return Collections.singletonList(new TicketsResponse.Ticket());
        return tickets;
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

        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction);
    }
}
