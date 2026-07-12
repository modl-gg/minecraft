package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class ChromeMenu extends BaseMenu {

    protected final MenuChrome chrome;

    public ChromeMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                      Consumer<CirrusPlayerWrapper> backAction, MenuChrome chrome) {
        super(platform, httpClient, viewerUuid, viewerName, backAction);
        this.chrome = chrome;
    }

    public ChromeMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                      Consumer<CirrusPlayerWrapper> backAction, MenuChrome chrome, CirrusInventoryType inventoryType) {
        super(platform, httpClient, viewerUuid, viewerName, backAction, inventoryType);
        this.chrome = chrome;
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();
        chrome.registerNavigation(this::registerActionHandler);
    }

    protected void buildHeader() {
        for (Map.Entry<Integer, CirrusItem> entry : chrome.headerItems(false).entrySet()) {
            set(entry.getValue().slot(entry.getKey()));
        }
        addBackButton();
    }

    protected void buildCompactHeader() {
        for (Map.Entry<Integer, CirrusItem> entry : chrome.headerItems(true).entrySet()) {
            set(entry.getValue().slot(entry.getKey()));
        }
        addCompactBackButton();
    }
}
