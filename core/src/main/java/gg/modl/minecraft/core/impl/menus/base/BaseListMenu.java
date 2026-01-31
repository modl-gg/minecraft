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

import java.util.Collection;
import java.util.Collections;
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

    /**
     * Placeholder type used when the list is empty.
     * This prevents Cirrus from shrinking the inventory to 18 slots.
     */
    public static final Object EMPTY_PLACEHOLDER = new Object();

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
        fixedSize(CirrusInventoryType.GENERIC_9X6);
    }

    /**
     * Override to provide static items for header and footer rows.
     * Subclasses should call super.intercept() and add their own items.
     *
     * Layout (6-row, 54 slots):
     * Row 0 (0-8): blank (intercepted)
     * Row 1 (9-17): header items (intercepted, subclass adds items)
     * Row 2 (18-26): blank (intercepted)
     * Row 3 (27-35): * x x x x x x x * - content at 28-34, ends intercepted
     * Row 4 (36-44): * * < * y * > * * - navigation row (intercepted)
     * Row 5 (45-53): Q * * * * * * * * - back button at 45 (intercepted)
     *
     * Content slots: 28-34 (7 items per page)
     * All other slots are intercepted to prevent AbstractBrowser from using them.
     */
    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = new HashMap<>();

        // Intercept ALL slots except content area (28-34)
        // This forces AbstractBrowser to place items only in slots 28-34

        // Row 0: slots 0-8 (blank)
        for (int i = 0; i <= 8; i++) {
            items.put(i, null);
        }

        // Row 1: slots 9-17 (header - subclass will override with actual items)
        for (int i = 9; i <= 17; i++) {
            items.put(i, null);
        }

        // Row 2: slots 18-26 (blank)
        for (int i = 18; i <= 26; i++) {
            items.put(i, null);
        }

        // Row 3: intercept slot 27 and 35 only (28-34 are content)
        items.put(27, null);
        items.put(35, null);

        // Row 4: slots 36-44 (navigation row)
        for (int i = 36; i <= 44; i++) {
            items.put(i, null);
        }

        // Row 5: slots 45-53 (back button row)
        for (int i = 45; i <= 53; i++) {
            items.put(i, null);
        }

        // Add back button at Q position (slot 45) only if there's a back action
        if (backAction != null) {
            items.put(MenuSlots.BACK_BUTTON, MenuItems.backButton());
        }

        // Add pagination controls at fixed positions
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

    /**
     * Creates a placeholder item for empty lists.
     * This is used to prevent Cirrus from shrinking the inventory.
     */
    protected CirrusItem createEmptyPlaceholder(String message) {
        return CirrusItem.of(
                CirrusItemType.GRAY_STAINED_GLASS_PANE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + message),
                MenuItems.lore(MenuItems.COLOR_DARK_GRAY + "No items to display")
        );
    }

    /**
     * Override this method in subclasses to return the actual elements.
     * The base implementation returns an empty collection.
     */
    protected Collection<T> getElements() {
        return Collections.emptyList();
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
