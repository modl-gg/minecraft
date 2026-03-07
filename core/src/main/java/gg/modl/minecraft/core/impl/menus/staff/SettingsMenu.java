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
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SettingsMenu extends BaseStaffMenu {
    private final String panelUrl;
    private final boolean canModifySettings, canManageStaff;

    public SettingsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                        boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;

        Cache cache = platform.getCache();
        if (cache != null) {
            this.canModifySettings = cache.hasPermission(viewerUuid, Permissions.SETTINGS_MODIFY);
            this.canManageStaff = cache.hasPermission(viewerUuid, Permissions.STAFF_MANAGE);
        } else {
            this.canModifySettings = isAdmin;
            this.canManageStaff = isAdmin;
        }

        title("Settings");
        activeTab = StaffTab.SETTINGS;
        buildMenu();
    }

    private void buildMenu() {
        buildHeader();

        List<String> infoLore = new ArrayList<>();
        infoLore.add(MenuItems.COLOR_GRAY + "Username: " + MenuItems.COLOR_WHITE + viewerName);
        infoLore.add(MenuItems.COLOR_GRAY + "Role: " + MenuItems.COLOR_WHITE + (isAdmin ? "Administrator" : "Staff"));
        if (canModifySettings || canManageStaff) {
            infoLore.add("");
            infoLore.add(MenuItems.COLOR_GRAY + "Permissions:");
            if (canModifySettings)
                infoLore.add(MenuItems.COLOR_GREEN + "  ✓ " + MenuItems.COLOR_GRAY + "Modify Settings");
            if (canManageStaff)
                infoLore.add(MenuItems.COLOR_GREEN + "  ✓ " + MenuItems.COLOR_GRAY + "Manage Staff");
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

        Cache cache = platform.getCache();
        gg.modl.minecraft.core.impl.cache.PlayerProfile viewerProfile = cache != null ? cache.getPlayerProfile(viewerUuid) : null;
        boolean staffNotificationsEnabled = viewerProfile != null && viewerProfile.isStaffNotificationsEnabled();
        set(CirrusItem.of(
                staffNotificationsEnabled ? CirrusItemType.LIME_DYE : CirrusItemType.GRAY_DYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Staff Notifications: " +
                        (staffNotificationsEnabled ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Toggle staff notifications"
                )
        ).slot(MenuSlots.SETTINGS_NOTIFICATIONS).actionHandler("toggleNotifications"));

        set(CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Staff List"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View all staff members",
                        MenuItems.COLOR_GRAY + "and their online status"
                )
        ).slot(MenuSlots.SETTINGS_TICKETS).actionHandler("staffMembers"));

        if (canModifySettings) {
            set(CirrusItem.of(
                    CirrusItemType.BLAZE_ROD,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Edit Roles"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Modify role permissions"
                    )
            ).slot(MenuSlots.SETTINGS_ROLES).actionHandler("editRoles"));
        }

        if (canManageStaff) {
            set(CirrusItem.of(
                    CirrusItemType.IRON_CHESTPLATE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Manage Staff"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Manage staff roles"
                    )
            ).slot(MenuSlots.SETTINGS_STAFF).actionHandler("manageStaff"));
        }

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

        registerActionHandler("toggleNotifications", this::handleToggleNotifications);

        Consumer<CirrusPlayerWrapper> returnToSettings = p ->
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p);

        registerActionHandler("staffMembers", ActionHandlers.openMenu(
                new StaffMembersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings)));

        if (canModifySettings) {
            registerActionHandler("editRoles", ActionHandlers.openMenu(
                    new RoleListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings)));

            registerActionHandler("reloadModl", this::handleReloadModl);
        }

        if (canManageStaff) {
            registerActionHandler("manageStaff", ActionHandlers.openMenu(
                    new StaffListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings)));
        }

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("openSettings", click -> {});
    }

    private void handleToggleNotifications(Click click) {
        Cache cache = platform.getCache();
        gg.modl.minecraft.core.impl.cache.PlayerProfile profile = cache != null ? cache.getPlayerProfile(viewerUuid) : null;
        if (profile != null) {
            boolean currentValue = profile.isStaffNotificationsEnabled();
            profile.setStaffNotificationsEnabled(!currentValue);
            sendMessage(MenuItems.COLOR_GREEN + "Staff notifications " + (!currentValue ? "enabled" : "disabled"));
        } else {
            sendMessage(MenuItems.COLOR_RED + "Unable to save preference - cache unavailable");
        }

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

        ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction))
                .handle(click);
    }
}
