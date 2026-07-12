package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.staff.OnlinePlayersMenu;
import gg.modl.minecraft.core.impl.menus.staff.RecentPunishmentsMenu;
import gg.modl.minecraft.core.impl.menus.staff.SettingsMenu;
import gg.modl.minecraft.core.impl.menus.staff.StaffReportsMenu;
import gg.modl.minecraft.core.impl.menus.staff.TicketsMenu;
import gg.modl.minecraft.core.util.ClickableJsonMessage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class StaffNavigationHandlers {
    private StaffNavigationHandlers() {}

    public static void displayWhenLoaded(Platform platform, CompletableFuture<Void> dataFuture,
                                         CirrusPlayerWrapper player, Consumer<CirrusPlayerWrapper> displayAction) {
        MenuAsync.displayWhenLoaded(platform, dataFuture, player, displayAction);
    }

    public static void registerAll(
            BiConsumer<String, Consumer<Click>> registrar,
            Platform platform, ModlHttpClient httpClient,
            UUID viewerUuid, String viewerName,
            boolean isAdmin, String panelUrl) {

        registrar.accept("openOnlinePlayers", click -> {
            OnlinePlayersMenu menu = new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null);
            displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });

        registrar.accept("openReports", click -> {
            StaffReportsMenu menu = new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null);
            displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });

        registrar.accept("openPunishments", click -> {
            RecentPunishmentsMenu menu = new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null);
            displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });

        registrar.accept("openTickets", click -> {
            TicketsMenu menu = new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null);
            displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });

        registrar.accept("openPanel", click -> {
            click.clickedMenu().close();
            String panelJson = ClickableJsonMessage.empty()
                    .extra(ClickableJsonMessage.text("Staff Panel: ").color("gold"))
                    .extra(ClickableJsonMessage.text(panelUrl)
                            .color("aqua")
                            .underlined(true)
                            .openUrl(panelUrl)
                            .hoverText("Click to open in browser"))
                    .toJson();
            platform.sendJsonMessage(viewerUuid, panelJson);
        });

        registrar.accept("openSettings", click ->
                ActionHandlers.openMenu(new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)).handle(click));
    }
}
