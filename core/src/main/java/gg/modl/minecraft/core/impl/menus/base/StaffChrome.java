package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class StaffChrome implements MenuChrome {

    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final UUID viewerUuid;
    private final String viewerName;
    private final boolean isAdmin;
    private final String panelUrl;

    public StaffChrome(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       boolean isAdmin, String panelUrl) {
        this.platform = platform;
        this.httpClient = httpClient;
        this.viewerUuid = viewerUuid;
        this.viewerName = viewerName;
        this.isAdmin = isAdmin;
        this.panelUrl = panelUrl;
    }

    @Override
    public Map<Integer, CirrusItem> headerItems(boolean compact) {
        return compact ? StaffTabItems.createCompactItems() : StaffTabItems.createItems();
    }

    @Override
    public void registerNavigation(BiConsumer<String, Consumer<Click>> registrar) {
        StaffNavigationHandlers.registerAll(registrar, platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);
    }
}
