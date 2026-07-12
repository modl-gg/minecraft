package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class ChromeListMenu<T> extends BaseListMenu<T> {

    protected final MenuChrome chrome;

    public ChromeListMenu(String title, Platform platform, ModlHttpClient httpClient,
                          UUID viewerUuid, String viewerName, Consumer<CirrusPlayerWrapper> backAction, MenuChrome chrome) {
        super(title, platform, httpClient, viewerUuid, viewerName, backAction);
        this.chrome = chrome;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);
        items.putAll(chrome.headerItems(false));
        return items;
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();
        chrome.registerNavigation(this::registerActionHandler);
    }
}
