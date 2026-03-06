package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class for Staff Menu screens.
 * Provides the common header with staff navigation items.
 */
public abstract class BaseStaffMenu extends BaseMenu {

    protected StaffTab activeTab = StaffTab.NONE;
    protected final boolean isAdmin;

    public BaseStaffMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, backAction);
        this.isAdmin = isAdmin;
    }

    public BaseStaffMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType) {
        super(platform, httpClient, viewerUuid, viewerName, backAction, inventoryType);
        this.isAdmin = isAdmin;
    }

    protected void buildHeader() {
        fillBorders();
        for (Map.Entry<Integer, CirrusItem> entry : StaffTabItems.createItems().entrySet()) {
            set(entry.getValue().slot(entry.getKey()));
        }
        addBackButton();
    }

    protected void buildCompactHeader() {
        for (Map.Entry<Integer, CirrusItem> entry : StaffTabItems.createCompactItems().entrySet()) {
            set(entry.getValue().slot(entry.getKey()));
        }
        addCompactBackButton();
    }

}
