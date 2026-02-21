package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.LinkedTicketItems;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.*;
import java.util.function.Consumer;

/**
 * Staff variant of the View Linked Tickets Menu.
 * Uses the staff menu header instead of the inspect menu header.
 */
public class StaffViewLinkedTicketsMenu extends BaseStaffListMenu<TicketsResponse.Ticket> {

    private final List<TicketsResponse.Ticket> tickets = new ArrayList<>();
    private final String panelUrl;

    public StaffViewLinkedTicketsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                      boolean isAdmin, String panelUrl, List<String> ticketIds,
                                      Consumer<CirrusPlayerWrapper> backAction) {
        super("Linked Tickets", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;

        activeTab = StaffTab.PUNISHMENTS;

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

        registerActionHandler("openOnlinePlayers", ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPunishments", ActionHandlers.openMenu(
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openTickets", ActionHandlers.openMenu(
                new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPanel", click -> {
            click.clickedMenu().close();
            String escapedUrl = panelUrl.replace("\"", "\\\"");
            String panelJson = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"Staff Panel: \",\"color\":\"gold\"}," +
                "{\"text\":\"%s\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to open in browser\"}}]}",
                escapedUrl, panelUrl
            );
            platform.sendJsonMessage(viewerUuid, panelJson);
        });

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));
    }
}
