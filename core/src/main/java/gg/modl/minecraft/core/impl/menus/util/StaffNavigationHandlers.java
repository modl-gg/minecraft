package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.model.Click;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.staff.OnlinePlayersMenu;
import gg.modl.minecraft.core.impl.menus.staff.RecentPunishmentsMenu;
import gg.modl.minecraft.core.impl.menus.staff.SettingsMenu;
import gg.modl.minecraft.core.impl.menus.staff.StaffReportsMenu;
import gg.modl.minecraft.core.impl.menus.staff.TicketsMenu;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class StaffNavigationHandlers {
    private StaffNavigationHandlers() {}

    public static void registerAll(
            BiConsumer<String, Consumer<Click>> registrar,
            Platform platform, ModlHttpClient httpClient,
            UUID viewerUuid, String viewerName,
            boolean isAdmin, String panelUrl) {

        registrar.accept("openOnlinePlayers", click ->
                ActionHandlers.openMenu(new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)).handle(click));

        registrar.accept("openReports", click ->
                ActionHandlers.openMenu(new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)).handle(click));

        registrar.accept("openPunishments", click ->
                ActionHandlers.openMenu(new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)).handle(click));

        registrar.accept("openTickets", click ->
                ActionHandlers.openMenu(new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)).handle(click));

        registrar.accept("openPanel", click -> {
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

        registrar.accept("openSettings", click ->
                ActionHandlers.openMenu(new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)).handle(click));
    }
}
