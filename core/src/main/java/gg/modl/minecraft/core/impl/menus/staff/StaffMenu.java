package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffMenu;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;

import java.util.UUID;
import java.util.function.Consumer;

public class StaffMenu extends BaseStaffMenu {
    private final String panelUrl;

    public StaffMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, isAdmin, backAction, CirrusInventoryType.GENERIC_9X3);
        this.panelUrl = panelUrl;

        title("Staff Panel");
        activeTab = StaffTab.NONE;
        buildCompactHeader();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                (name, handler) -> registerActionHandler(name, handler),
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);
    }

    public String getPanelUrl() {
        return panelUrl;
    }
}
