package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import dev.simplix.protocolize.data.inventory.InventoryType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class for all MODL menus.
 * Provides common functionality like glass pane borders, back button handling,
 * and access to platform/HTTP client.
 */
public abstract class BaseMenu extends SimpleMenu {

    protected final Platform platform;
    protected final ModlHttpClient httpClient;
    protected final UUID viewerUuid;
    protected final String viewerName;
    protected final Consumer<CirrusPlayerWrapper> backAction;

    /**
     * Create a new base menu with default 6-row size.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the player viewing the menu
     * @param viewerName The name of the player viewing the menu
     * @param backAction Action to perform when back button is clicked (null if none)
     */
    public BaseMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, backAction, InventoryType.GENERIC_9X6);
    }

    /**
     * Create a new base menu with custom size.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the player viewing the menu
     * @param viewerName The name of the player viewing the menu
     * @param backAction Action to perform when back button is clicked (null if none)
     * @param inventoryType The inventory type/size for this menu
     */
    public BaseMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Consumer<CirrusPlayerWrapper> backAction, InventoryType inventoryType) {
        super();
        this.platform = platform;
        this.httpClient = httpClient;
        this.viewerUuid = viewerUuid;
        this.viewerName = viewerName;
        this.backAction = backAction;

        type(inventoryType);
    }

    /**
     * Fill border slots - now a no-op since GUI uses blank space instead of glass.
     * @deprecated No longer needed - GUI spec uses blank space, not glass panes.
     */
    @Deprecated
    protected void fillBorders() {
        // No-op - blank space is preferred per GUI spec
    }

    /**
     * Fill all borders for a 6-row menu.
     * @deprecated No longer needed - GUI spec uses blank space, not glass panes.
     */
    @Deprecated
    protected void fillBorders6Row() {
        // No-op
    }

    /**
     * Add the back button if there is a back action.
     */
    protected void addBackButton() {
        if (backAction != null) {
            set(MenuItems.backButton().slot(MenuSlots.BACK_BUTTON));
        }
    }

    /**
     * Send a message to the viewer.
     */
    protected void sendMessage(String message) {
        platform.sendMessage(viewerUuid, message);
    }

    /**
     * Create an item with click handler.
     */
    protected CirrusItem createItem(ItemType type, String title, String actionHandler, String... lore) {
        return CirrusItem.of(type, ChatElement.ofLegacyText(title), MenuItems.lore(lore))
                .actionHandler(actionHandler);
    }

    @Override
    protected void registerActionHandlers() {
        // Handle back button
        registerActionHandler("back", this::handleBack);
    }

    /**
     * Handle the back button click.
     */
    protected void handleBack(Click click) {
        click.clickedMenu().close();
        if (backAction != null) {
            backAction.accept(click.player());
        }
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
