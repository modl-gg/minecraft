package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.ChromeListMenu;
import gg.modl.minecraft.core.impl.menus.base.MenuChrome;
import gg.modl.minecraft.core.impl.menus.util.LinkedTicketItems;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ViewLinkedTicketsMenu extends ChromeListMenu<TicketsResponse.Ticket> {
    private final List<TicketsResponse.Ticket> tickets = new ArrayList<>();
    @Getter private final CompletableFuture<Void> dataFuture;

    public ViewLinkedTicketsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                 MenuChrome chrome, List<String> ticketIds, Consumer<CirrusPlayerWrapper> backAction) {
        super("Linked Tickets", platform, httpClient, viewerUuid, viewerName, backAction, chrome);
        this.dataFuture = LinkedTicketItems.loadTickets(httpClient, ticketIds).thenAccept(tickets::addAll);
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
}
