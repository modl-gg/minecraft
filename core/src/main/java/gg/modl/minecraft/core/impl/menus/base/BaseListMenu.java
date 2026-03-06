package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.menus.AbstractBrowser;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class for list/browser menus that use AbstractBrowser.
 * Provides the intercept method for static header and footer items.
 *
 * @param <T> The type of elements displayed in the browser
 */
public abstract class BaseListMenu<T> extends AbstractBrowser<T> {

    protected final Platform platform;
    protected final ModlHttpClient httpClient;
    protected final UUID viewerUuid;
    protected final String viewerName;
    protected final Consumer<CirrusPlayerWrapper> backAction;

    public BaseListMenu(String title, Platform platform, ModlHttpClient httpClient,
                        UUID viewerUuid, String viewerName, Consumer<CirrusPlayerWrapper> backAction) {
        super();
        this.platform = platform;
        this.httpClient = httpClient;
        this.viewerUuid = viewerUuid;
        this.viewerName = viewerName;
        this.backAction = backAction;

        title(title);
        fixedSize(CirrusInventoryType.GENERIC_9X6);
    }

    /**
     * Intercept all slots except content area (28-34) so AbstractBrowser only places items there.
     * Subclasses should call super.intercept() and add their own header items.
     */
    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = new HashMap<>();

        for (int i = 0; i <= 26; i++) items.put(i, null);

        items.put(27, null);
        items.put(35, null);

        for (int i = 36; i <= 53; i++) items.put(i, null);

        if (backAction != null) items.put(MenuSlots.BACK_BUTTON, MenuItems.backButton());
        items.put(MenuSlots.PAGE_PREV, MenuItems.previousPageButton());
        items.put(MenuSlots.PAGE_NEXT, MenuItems.nextPageButton());

        return items;
    }

    /** Creates a placeholder item to prevent Cirrus from shrinking the inventory when the list is empty. */
    protected CirrusItem createEmptyPlaceholder(String message) {
        return CirrusItem.of(
                CirrusItemType.GRAY_STAINED_GLASS_PANE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + message),
                MenuItems.lore(MenuItems.COLOR_DARK_GRAY + "No items to display")
        );
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("back", (dev.simplix.cirrus.actionhandler.ActionHandler) click -> {
            handleBack(click);
            return dev.simplix.cirrus.model.CallResult.DENY_GRABBING;
        });
    }

    protected void handleBack(Click click) {
        click.clickedMenu().close();
        if (backAction != null) backAction.accept(click.player());
    }

    protected void sendMessage(String message) {
        platform.sendMessage(viewerUuid, message);
    }

}
