package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.inspect.AltsMenu;
import gg.modl.minecraft.core.impl.menus.inspect.HistoryMenu;
import gg.modl.minecraft.core.impl.menus.inspect.NotesMenu;
import gg.modl.minecraft.core.impl.menus.inspect.PunishMenu;
import gg.modl.minecraft.core.impl.menus.inspect.ReportsMenu;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class InspectNavigationHandlers {
    private InspectNavigationHandlers() {}

    public static void registerAll(
            BiConsumer<String, Consumer<Click>> registrar,
            Platform platform, ModlHttpClient httpClient,
            UUID viewerUuid, String viewerName,
            Account targetAccount,
            Consumer<CirrusPlayerWrapper> backAction) {
        registerAll(registrar, platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public static void registerAll(
            BiConsumer<String, Consumer<Click>> registrar,
            Platform platform, ModlHttpClient httpClient,
            UUID viewerUuid, String viewerName,
            Account targetAccount,
            Consumer<CirrusPlayerWrapper> backAction,
            InspectContext inspectContext) {

        registrar.accept("openNotes", click ->
                ActionHandlers.openMenu(new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext)).handle(click));

        registrar.accept("openAlts", click -> {
            AltsMenu menu = new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
            MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });

        registrar.accept("openHistory", click -> {
            HistoryMenu menu = new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
            MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });

        registrar.accept("openReports", click -> {
            ReportsMenu menu = new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
            MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });

        registrar.accept("openPunish", click -> {
            PunishMenu menu = new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
            MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });
    }
}
