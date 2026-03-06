package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;

import java.util.UUID;
import java.util.function.Consumer;

public class InspectMenu extends BaseInspectMenu {
    public InspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, CirrusInventoryType.GENERIC_9X3);

        title("Inspect: " + targetName);
        activeTab = InspectTab.NONE;
        buildCompactHeader();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        Consumer<CirrusPlayerWrapper> inspectBackAction = player ->
                new InspectMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                        .display(player);

        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, inspectBackAction);
    }
}
