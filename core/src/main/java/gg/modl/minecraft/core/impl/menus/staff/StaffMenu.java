package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.data.inventory.InventoryType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Primary Staff Menu - shows navigation to staff tools.
 * Accessible via /staff
 */
public class StaffMenu extends BaseStaffMenu {

    private final String panelUrl;

    /**
     * Create a new staff menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL for linking
     * @param backAction Action to perform when back button is clicked (null if opened directly)
     */
    public StaffMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, isAdmin, backAction, InventoryType.GENERIC_9X3);
        this.panelUrl = panelUrl;

        title("Staff Panel");
        activeTab = StaffTab.NONE;
        buildCompactHeader();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Primary tabs should NOT have back button - they are all equivalent primary views
        // Pass null as backAction for all primary tab menus
        registerActionHandler("openOnlinePlayers", ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPunishments", ActionHandlers.openMenu(
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openTickets", ActionHandlers.openMenu(
                new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        // Panel link just sends a message, no menu transition
        registerActionHandler("openPanel", click -> {
            sendMessage("");
            sendMessage(MenuItems.COLOR_GOLD + "Staff Panel:");
            sendMessage(MenuItems.COLOR_AQUA + panelUrl);
            sendMessage("");
        });
    }

    /**
     * Get the panel URL.
     */
    public String getPanelUrl() {
        return panelUrl;
    }
}
