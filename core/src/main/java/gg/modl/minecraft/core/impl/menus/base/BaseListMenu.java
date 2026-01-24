package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.menus.AbstractBrowser;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.data.inventory.InventoryType;
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

    /**
     * Create a new list menu.
     *
     * @param title The menu title
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the player viewing the menu
     * @param viewerName The name of the player viewing the menu
     * @param backAction Action to perform when back button is clicked (null if none)
     */
    public BaseListMenu(String title, Platform platform, ModlHttpClient httpClient,
                        UUID viewerUuid, String viewerName, Consumer<CirrusPlayerWrapper> backAction) {
        super();
        this.platform = platform;
        this.httpClient = httpClient;
        this.viewerUuid = viewerUuid;
        this.viewerName = viewerName;
        this.backAction = backAction;

        title(title);
        fixedSize(InventoryType.GENERIC_9X6);
    }

    /**
     * Override to provide static items for header and footer rows.
     * Subclasses should call super.intercept() and add their own items.
     */
    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = new HashMap<>();

        // Fill header row (slots 0-8) with glass panes
        for (int i = 0; i <= 8; i++) {
            items.put(i, MenuItems.glassPaneFiller());
        }

        // Fill second row (slots 9-17) with glass panes for header items
        for (int i = 9; i <= 17; i++) {
            items.put(i, MenuItems.glassPaneFiller());
        }

        // Fill footer row (slots 45-53) with glass panes
        for (int i = 45; i <= 53; i++) {
            items.put(i, MenuItems.glassPaneFiller());
        }

        // Add back button if there's a back action
        if (backAction != null) {
            items.put(MenuSlots.BACK_BUTTON, MenuItems.backButton());
        }

        // Add pagination controls
        items.put(MenuSlots.PAGE_PREV, MenuItems.previousPageButton());
        items.put(MenuSlots.PAGE_NEXT, MenuItems.nextPageButton());

        return items;
    }

    /**
     * Get header items to display in the second row.
     * Override in subclasses to add inspect/staff header items.
     */
    protected Map<Integer, CirrusItem> getHeaderItems() {
        return new HashMap<>();
    }

    @Override
    protected void registerActionHandlers() {
        // Handle back button - use ActionHandler interface for unambiguous method call
        registerActionHandler("back", (dev.simplix.cirrus.actionhandler.ActionHandler) click -> {
            handleBack(click);
            return dev.simplix.cirrus.model.CallResult.DENY_GRABBING;
        });
    }

    /**
     * Handle the back button click.
     */
    protected void handleBack(Click click) {
        if (backAction != null) {
            click.clickedMenu().close();
            backAction.accept(click.player());
        } else {
            click.clickedMenu().close();
        }
    }

    /**
     * Send a message to the viewer.
     */
    protected void sendMessage(String message) {
        platform.sendMessage(viewerUuid, message);
    }

    /**
     * Get the platform instance.
     */
    public Platform getPlatform() {
        return platform;
    }

    /**
     * Get the HTTP client.
     */
    public ModlHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Get the viewer's UUID.
     */
    public UUID getViewerUuid() {
        return viewerUuid;
    }

    /**
     * Get the viewer's name.
     */
    public String getViewerName() {
        return viewerName;
    }

    /**
     * Get the back action.
     */
    public Consumer<CirrusPlayerWrapper> getBackAction() {
        return backAction;
    }
}
