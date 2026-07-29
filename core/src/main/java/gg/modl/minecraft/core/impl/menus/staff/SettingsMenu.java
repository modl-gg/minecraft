package gg.modl.minecraft.core.impl.menus.staff;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.MenuAsync;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SettingsMenu extends BaseStaffMenu {
    private final boolean canModifySettings, canManageStaff;

    public SettingsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                        boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction);

        Cache cache = PluginServices.cache();
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
        }

        set(CirrusItem.of(
                CirrusItemType.ANVIL,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Information"),
                MenuItems.lore(infoLore)
        ).slot(MenuSlots.SETTINGS_INFO));

        set(CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Staff List"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View all staff members",
                        MenuItems.COLOR_GRAY + "and their online status"
                )
        ).slot(MenuSlots.SETTINGS_STAFF_LIST).actionHandler("staffMembers"));

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
                    CirrusItemType.BLAZE_ROD,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Edit Roles"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Modify role permissions"
                    )
            ).slot(MenuSlots.SETTINGS_ROLES).actionHandler("editRoles"));
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

        Consumer<CirrusPlayerWrapper> returnToSettings = p ->
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p);

        registerActionHandler("staffMembers", click -> {
            StaffMembersMenu menu = new StaffMembersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin,
                    panelUrl, returnToSettings);
            MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
        });

        if (canModifySettings) {
            registerActionHandler("editRoles", click -> {
                RoleListMenu menu = new RoleListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, returnToSettings);
                MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
            });

            registerActionHandler("reloadModl", this::handleReloadModl);
        }

        if (canManageStaff) {
            registerActionHandler("manageStaff", click -> {
                StaffListMenu menu = new StaffListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin,
                        panelUrl, returnToSettings);
                MenuAsync.displayWhenLoaded(platform, menu.getDataFuture(), click.player(), menu::display);
            });
        }

        registerActionHandler("openSettings", click -> {});
    }

    private void handleReloadModl(Click click) {
        sendMessage(MenuItems.COLOR_GREEN + "Reloading modl.gg configuration...");
        try {
            LocaleManager localeManager = PluginServices.locale();
            if (localeManager != null) {
                localeManager.reloadLocale();
                Cache cache = PluginServices.cache();
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
