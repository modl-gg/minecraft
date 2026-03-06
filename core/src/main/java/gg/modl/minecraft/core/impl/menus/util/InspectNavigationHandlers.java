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

        registrar.accept("openNotes", click ->
                ActionHandlers.openMenu(new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)).handle(click));

        registrar.accept("openAlts", click ->
                ActionHandlers.openMenu(new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)).handle(click));

        registrar.accept("openHistory", click ->
                ActionHandlers.openMenu(new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)).handle(click));

        registrar.accept("openReports", click ->
                ActionHandlers.openMenu(new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)).handle(click));

        registrar.accept("openPunish", click ->
                ActionHandlers.openMenu(new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)).handle(click));
    }
}
