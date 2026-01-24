package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
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
        super(platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;

        title("Staff Panel");
        activeTab = StaffTab.NONE;
        buildHeader();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Open Online Players menu
        registerActionHandler("openOnlinePlayers", this::openOnlinePlayers);

        // Open Reports menu
        registerActionHandler("openReports", this::openReports);

        // Open Recent Punishments menu
        registerActionHandler("openPunishments", this::openPunishments);

        // Open Tickets menu
        registerActionHandler("openTickets", this::openTickets);

        // Open Panel link
        registerActionHandler("openPanel", this::openPanel);

        // Open Settings menu
        registerActionHandler("openSettings", this::openSettings);
    }

    private Consumer<CirrusPlayerWrapper> getReturnToStaffAction() {
        return player -> new StaffMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                .display(player);
    }

    private void openOnlinePlayers(Click click) {
        click.clickedMenu().close();
        new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, getReturnToStaffAction())
                .display(click.player());
    }

    private void openReports(Click click) {
        click.clickedMenu().close();
        new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, getReturnToStaffAction())
                .display(click.player());
    }

    private void openPunishments(Click click) {
        click.clickedMenu().close();
        new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, getReturnToStaffAction())
                .display(click.player());
    }

    private void openTickets(Click click) {
        click.clickedMenu().close();
        new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, getReturnToStaffAction())
                .display(click.player());
    }

    private void openPanel(Click click) {
        // Send panel URL in chat
        sendMessage("");
        sendMessage(MenuItems.COLOR_GOLD + "Staff Panel:");
        sendMessage(MenuItems.COLOR_AQUA + panelUrl);
        sendMessage("");
    }

    private void openSettings(Click click) {
        click.clickedMenu().close();
        new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, getReturnToStaffAction())
                .display(click.player());
    }

    /**
     * Get the panel URL.
     */
    public String getPanelUrl() {
        return panelUrl;
    }
}
