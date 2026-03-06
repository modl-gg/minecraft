package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class for Staff Menu list screens (Online Players, Reports, Punishments, Tickets).
 * Provides the common header with staff navigation items.
 *
 * @param <T> The type of elements displayed in the browser
 */
public abstract class BaseStaffListMenu<T> extends BaseListMenu<T> {

    protected StaffTab activeTab = StaffTab.NONE;
    protected final boolean isAdmin;

    public BaseStaffListMenu(String title, Platform platform, ModlHttpClient httpClient,
                             UUID viewerUuid, String viewerName, boolean isAdmin,
                             Consumer<CirrusPlayerWrapper> backAction) {
        super(title, platform, httpClient, viewerUuid, viewerName, backAction);
        this.isAdmin = isAdmin;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);
        items.putAll(StaffTabItems.createItems());
        return items;
    }

}
