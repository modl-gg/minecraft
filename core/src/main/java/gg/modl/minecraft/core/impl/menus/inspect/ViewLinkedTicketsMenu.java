package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.LinkedTicketItems;

import java.util.*;
import java.util.function.Consumer;

/**
 * View Linked Tickets Menu - displays ticket details for tickets linked to a punishment.
 * Uses the inspect menu header for consistent navigation.
 */
public class ViewLinkedTicketsMenu extends BaseInspectListMenu<TicketsResponse.Ticket> {

    private final List<TicketsResponse.Ticket> tickets = new ArrayList<>();
    private final Consumer<CirrusPlayerWrapper> rootBackAction;

    /**
     * Create a new view linked tickets menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param ticketIds The IDs of tickets to display
     * @param backAction Action to return to parent menu
     * @param rootBackAction Root back action for primary tab navigation
     */
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
        if (tickets.isEmpty()) {
            return Collections.singletonList(new TicketsResponse.Ticket());
        }
        return tickets;
    }

    @Override
    protected CirrusItem map(TicketsResponse.Ticket ticket) {
        if (ticket.getId() == null) {
            return createEmptyPlaceholder("No linked tickets");
        }
        return LinkedTicketItems.mapTicket(ticket, platform);
    }

    @Override
    protected void handleClick(Click click, TicketsResponse.Ticket ticket) {
        LinkedTicketItems.handleTicketClick(click, ticket, platform, viewerUuid);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Inspect tab navigation handlers
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));
    }
}
