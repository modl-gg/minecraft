package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;

import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseStaffMenu extends ChromeMenu {

    protected StaffTab activeTab = StaffTab.NONE;
    protected final boolean isAdmin;

    public BaseStaffMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, backAction,
                new StaffChrome(platform, httpClient, viewerUuid, viewerName, isAdmin, null));
        this.isAdmin = isAdmin;
    }

    public BaseStaffMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType) {
        super(platform, httpClient, viewerUuid, viewerName, backAction,
                new StaffChrome(platform, httpClient, viewerUuid, viewerName, isAdmin, null), inventoryType);
        this.isAdmin = isAdmin;
    }

}
