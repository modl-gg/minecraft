package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;

import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseStaffListMenu<T> extends ChromeListMenu<T> {

    protected StaffTab activeTab = StaffTab.NONE;
    protected final boolean isAdmin;
    protected final String panelUrl;

    public BaseStaffListMenu(String title, Platform platform, ModlHttpClient httpClient,
                             UUID viewerUuid, String viewerName, boolean isAdmin, String panelUrl,
                             Consumer<CirrusPlayerWrapper> backAction) {
        super(title, platform, httpClient, viewerUuid, viewerName, backAction,
                new StaffChrome(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl));
        this.isAdmin = isAdmin;
        this.panelUrl = panelUrl;
    }

}
