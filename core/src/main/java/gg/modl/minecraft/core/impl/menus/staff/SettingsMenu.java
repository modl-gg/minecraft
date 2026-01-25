package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
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

    // Permission flags
    private final boolean canModifySettings;
    private final boolean canManageStaff;

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

        // Check specific permissions from cache
        Cache cache = platform.getCache();
        if (cache != null) {
            this.canModifySettings = cache.hasPermission(viewerUuid, "admin.settings.modify");
            this.canManageStaff = cache.hasPermission(viewerUuid, "admin.staff.manage");
        } else {
            // Fallback to isAdmin if cache not available
            this.canModifySettings = isAdmin;
            this.canManageStaff = isAdmin;
        }

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
        if (canModifySettings || canManageStaff) {
            infoLore.add("");
            infoLore.add(MenuItems.COLOR_GRAY + "Permissions:");
            if (canModifySettings) {
                infoLore.add(MenuItems.COLOR_GREEN + "  ✓ " + MenuItems.COLOR_GRAY + "Modify Settings");
            }
            if (canManageStaff) {
                infoLore.add(MenuItems.COLOR_GREEN + "  ✓ " + MenuItems.COLOR_GRAY + "Manage Staff");
            }
        }
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
        Cache cache = platform.getCache();
        boolean reportNotificationsEnabled = cache != null && cache.isReportNotificationsEnabled(viewerUuid);
        set(CirrusItem.of(
                reportNotificationsEnabled ? ItemType.LIME_DYE : ItemType.GRAY_DYE,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Report Notifications: " +
                        (reportNotificationsEnabled ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")),
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

        // Permission-based options
        // Edit Roles - requires modl.settings.modify
        if (canModifySettings) {
            set(CirrusItem.of(
                    ItemType.BLAZE_ROD,
                    ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Edit Roles"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Modify role permissions"
                    )
            ).slot(MenuSlots.SETTINGS_ROLES).actionHandler("editRoles"));
        }

        // Manage Staff - requires modl.staff.manage
        if (canManageStaff) {
            set(CirrusItem.of(
                    ItemType.IRON_CHESTPLATE,
                    ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Manage Staff"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Manage staff roles"
                    )
            ).slot(MenuSlots.SETTINGS_STAFF).actionHandler("manageStaff"));
        }

        // Reload Modl - requires modl.settings.modify
        if (canModifySettings) {
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

        // Permission-based handlers - secondary menus SHOULD have back action to return to Settings
        Consumer<CirrusPlayerWrapper> returnToSettings = p ->
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p);

        // Edit Roles - requires modl.settings.modify
        if (canModifySettings) {
            registerActionHandler("editRoles", ActionHandlers.openMenu(
                    new RoleListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings)));

            registerActionHandler("reloadModl", this::handleReloadModl);
        }

        // Manage Staff - requires modl.staff.manage
        if (canManageStaff) {
            registerActionHandler("manageStaff", ActionHandlers.openMenu(
                    new StaffListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings)));
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
        Cache cache = platform.getCache();
        if (cache != null) {
            boolean currentValue = cache.isReportNotificationsEnabled(viewerUuid);
            cache.setReportNotificationsEnabled(viewerUuid, !currentValue);
            sendMessage(MenuItems.COLOR_GREEN + "Report notifications " + (!currentValue ? "enabled" : "disabled"));
        } else {
            sendMessage(MenuItems.COLOR_RED + "Unable to save preference - cache unavailable");
        }

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
