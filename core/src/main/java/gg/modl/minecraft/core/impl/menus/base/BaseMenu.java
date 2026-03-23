package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseMenu extends SimpleMenu {

    protected final Platform platform;
    protected final ModlHttpClient httpClient;
    protected final UUID viewerUuid;
    protected final String viewerName;
    protected final Consumer<CirrusPlayerWrapper> backAction;

    public BaseMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, backAction, CirrusInventoryType.GENERIC_9X6);
    }

    public BaseMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType) {
        super();
        this.platform = platform;
        this.httpClient = httpClient;
        this.viewerUuid = viewerUuid;
        this.viewerName = viewerName;
        this.backAction = backAction;

        type(inventoryType);
    }

    protected void addBackButton() {
        if (backAction != null) set(MenuItems.backButton().slot(MenuSlots.BACK_BUTTON));
    }

    protected void addCompactBackButton() {
        if (backAction != null) set(MenuItems.backButton().slot(MenuSlots.COMPACT_BACK_BUTTON));
    }

    protected void sendMessage(String message) {
        platform.sendMessage(viewerUuid, message);
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("back", this::handleBack);
    }

    protected void handleBack(Click click) {
        click.clickedMenu().close();
        if (backAction != null) backAction.accept(click.player());
    }

}
