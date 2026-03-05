package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.locale.LocaleManager;

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
            infoLore.add(MenuItems.COLOR_GRAY + "modl.gg Status: " + MenuItems.COLOR_GREEN + "Healthy");
            // TODO: Add actual status from API
        }

        set(CirrusItem.of(
                CirrusItemType.ANVIL,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Information"),
                MenuItems.lore(infoLore)
        ).slot(MenuSlots.SETTINGS_INFO));

        // Slot 30: Staff Notifications toggle
        Cache cache = platform.getCache();
        boolean staffNotificationsEnabled = cache != null && cache.isStaffNotificationsEnabled(viewerUuid);
        set(CirrusItem.of(
                staffNotificationsEnabled ? CirrusItemType.LIME_DYE : CirrusItemType.GRAY_DYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Staff Notifications: " +
                        (staffNotificationsEnabled ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Toggle staff notifications"
                )
        ).slot(MenuSlots.SETTINGS_NOTIFICATIONS).actionHandler("toggleNotifications"));

        // Slot 31: Staff List
        set(CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Staff List"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View all staff members",
                        MenuItems.COLOR_GRAY + "and their online status"
                )
        ).slot(MenuSlots.SETTINGS_TICKETS).actionHandler("staffMembers"));

        // Permission-based options
        // Edit Roles - requires modl.settings.modify
        if (canModifySettings) {
            set(CirrusItem.of(
                    CirrusItemType.BLAZE_ROD,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Edit Roles"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Modify role permissions"
                    )
            ).slot(MenuSlots.SETTINGS_ROLES).actionHandler("editRoles"));
        }

        // Manage Staff - requires modl.staff.manage
        if (canManageStaff) {
            set(CirrusItem.of(
                    CirrusItemType.IRON_CHESTPLATE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Manage Staff"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Manage staff roles"
                    )
            ).slot(MenuSlots.SETTINGS_STAFF).actionHandler("manageStaff"));
        }

        // Reload Modl - requires modl.settings.modify
        if (canModifySettings) {
            set(CirrusItem.of(
                    CirrusItemType.REDSTONE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "Reload Modl"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Reload configuration and locale files"
                    )
            ).slot(MenuSlots.SETTINGS_RELOAD).actionHandler("reloadModl"));
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Toggle notifications handler
        registerActionHandler("toggleNotifications", this::handleToggleNotifications);

        // Staff Members handler
        Consumer<CirrusPlayerWrapper> returnToSettings = p ->
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p);

        registerActionHandler("staffMembers", ActionHandlers.openMenu(
                new StaffMembersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings)));

        // Permission-based handlers - secondary menus SHOULD have back action to return to Settings

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
            click.clickedMenu().close();
            String escapedUrl = panelUrl.replace("\"", "\\\"");
            String panelJson = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"Staff Panel: \",\"color\":\"gold\"}," +
                "{\"text\":\"%s\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to open in browser\"}}]}",
                escapedUrl, panelUrl
            );
            platform.sendJsonMessage(viewerUuid, panelJson);
        });

        registerActionHandler("openSettings", click -> {
            // Already here, do nothing
        });
    }

    private void handleToggleNotifications(Click click) {
        Cache cache = platform.getCache();
        if (cache != null) {
            boolean currentValue = cache.isStaffNotificationsEnabled(viewerUuid);
            cache.setStaffNotificationsEnabled(viewerUuid, !currentValue);
            sendMessage(MenuItems.COLOR_GREEN + "Staff notifications " + (!currentValue ? "enabled" : "disabled"));
        } else {
            sendMessage(MenuItems.COLOR_RED + "Unable to save preference - cache unavailable");
        }

        // Refresh menu - preserve backAction if present
        ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction))
                .handle(click);
    }

    private void handleReloadModl(Click click) {
        sendMessage(MenuItems.COLOR_GREEN + "Reloading modl.gg configuration...");
        try {
            LocaleManager localeManager = platform.getLocaleManager();
            if (localeManager != null) {
                localeManager.reloadLocale();
                // Clear caches so they're re-fetched/reloaded from disk
                Cache cache = platform.getCache();
                if (cache != null) {
                    cache.clearPunishmentTypes();
                    cache.clearPunishGuiConfig();
                }
                sendMessage(MenuItems.COLOR_GREEN + "Configuration reloaded successfully!");
            } else {
                sendMessage(MenuItems.COLOR_RED + "Locale manager not available.");
            }
        } catch (Exception e) {
            sendMessage(MenuItems.COLOR_RED + "Reload failed: " + e.getMessage());
        }

        // Refresh menu
        ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction))
                .handle(click);
    }

    /**
     * Get the panel URL.
     */
    public String getPanelUrl() {
        return panelUrl;
    }
}
