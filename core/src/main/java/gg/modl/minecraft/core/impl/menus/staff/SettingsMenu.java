package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Settings Menu - staff settings and admin options.
 */
public class SettingsMenu extends BaseStaffMenu {

    private final String panelUrl;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;
    private boolean reportNotifications = true; // Default enabled

    /**
     * Create a new settings menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param backAction Action to return to parent menu
     */
    public SettingsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                        boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;

        title("Settings");
        activeTab = StaffTab.SETTINGS;
        buildMenu();
    }

    private void buildMenu() {
        buildHeader();

        // Slot 28: Information (anvil)
        List<String> infoLore = new ArrayList<>();
        infoLore.add(MenuItems.COLOR_GRAY + "Username: " + MenuItems.COLOR_WHITE + viewerName);
        infoLore.add(MenuItems.COLOR_GRAY + "Role: " + MenuItems.COLOR_WHITE + (isAdmin ? "Administrator" : "Staff"));
        if (isAdmin) {
            infoLore.add("");
            infoLore.add(MenuItems.COLOR_GRAY + "MODL Status: " + MenuItems.COLOR_GREEN + "Healthy");
            // TODO: Add actual status from API
        }

        set(CirrusItem.of(
                ItemType.ANVIL,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Information"),
                MenuItems.lore(infoLore)
        ).slot(MenuSlots.SETTINGS_INFO));

        // Slot 30: Report Notifications toggle
        set(CirrusItem.of(
                reportNotifications ? ItemType.LIME_DYE : ItemType.GRAY_DYE,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Report Notifications: " +
                        (reportNotifications ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Toggle report notifications"
                )
        ).slot(MenuSlots.SETTINGS_NOTIFICATIONS).actionHandler("toggleNotifications"));

        // Slot 31: Recent Ticket Updates
        List<String> ticketLore = new ArrayList<>();
        ticketLore.add(MenuItems.COLOR_GRAY + "Recent ticket updates:");
        ticketLore.add(MenuItems.COLOR_DARK_GRAY + "(No subscribed tickets)");
        // TODO: Fetch subscribed tickets when endpoint available
        ticketLore.add("");
        ticketLore.add(MenuItems.COLOR_YELLOW + "Right-click to cycle through");
        ticketLore.add(MenuItems.COLOR_YELLOW + "Left-click to open selected");

        set(CirrusItem.of(
                ItemType.BOOK,
                ChatElement.ofLegacyText(MenuItems.COLOR_AQUA + "Recent Ticket Updates"),
                MenuItems.lore(ticketLore)
        ).slot(MenuSlots.SETTINGS_TICKETS).actionHandler("ticketUpdates"));

        // Admin-only options
        if (isAdmin) {
            // Slot 32: Edit Roles
            set(CirrusItem.of(
                    ItemType.BLAZE_ROD,
                    ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Edit Roles"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Modify role permissions"
                    )
            ).slot(MenuSlots.SETTINGS_ROLES).actionHandler("editRoles"));

            // Slot 33: Manage Staff
            set(CirrusItem.of(
                    ItemType.IRON_CHESTPLATE,
                    ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Manage Staff"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Manage staff roles"
                    )
            ).slot(MenuSlots.SETTINGS_STAFF).actionHandler("manageStaff"));

            // Slot 34: Reload Modl
            set(CirrusItem.of(
                    ItemType.REDSTONE,
                    ChatElement.ofLegacyText(MenuItems.COLOR_RED + "Reload Modl"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Reload messages config"
                    )
            ).slot(MenuSlots.SETTINGS_RELOAD).actionHandler("reloadModl"));
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Toggle notifications handler
        registerActionHandler("toggleNotifications", this::handleToggleNotifications);

        // Ticket updates handler
        registerActionHandler("ticketUpdates", this::handleTicketUpdates);

        // Admin handlers - secondary menus SHOULD have back action to return to Settings
        if (isAdmin) {
            Consumer<CirrusPlayerWrapper> returnToSettings = p ->
                    new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p);

            registerActionHandler("editRoles", ActionHandlers.openMenu(
                    new RoleListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings)));

            registerActionHandler("manageStaff", ActionHandlers.openMenu(
                    new StaffListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings)));

            registerActionHandler("reloadModl", this::handleReloadModl);
        }

        // Override header navigation - primary tabs should NOT pass backAction
        registerActionHandler("openOnlinePlayers", ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPunishments", ActionHandlers.openMenu(
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openTickets", ActionHandlers.openMenu(
                new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPanel", click -> {
            sendMessage("");
            sendMessage(MenuItems.COLOR_GOLD + "Staff Panel:");
            sendMessage(MenuItems.COLOR_AQUA + panelUrl);
            sendMessage("");
        });

        registerActionHandler("openSettings", click -> {
            // Already here, do nothing
        });
    }

    private void handleToggleNotifications(Click click) {
        reportNotifications = !reportNotifications;
        // TODO: Save preference to API when endpoint available
        sendMessage(MenuItems.COLOR_GREEN + "Report notifications " + (reportNotifications ? "enabled" : "disabled"));

        // Refresh menu - preserve backAction if present
        ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction))
                .handle(click);
    }

    private void handleTicketUpdates(Click click) {
        // TODO: Cycle through subscribed tickets when data available
        sendMessage(MenuItems.COLOR_YELLOW + "No subscribed tickets to display");
    }

    private void handleReloadModl(Click click) {
        // TODO: Implement config reload
        sendMessage(MenuItems.COLOR_GREEN + "Reloading MODL configuration...");
        sendMessage(MenuItems.COLOR_YELLOW + "Config reload not yet implemented");
    }

    /**
     * Get the panel URL.
     */
    public String getPanelUrl() {
        return panelUrl;
    }
}
