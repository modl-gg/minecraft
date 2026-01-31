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
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Role Permission Edit Menu - edit permissions for a role.
 * Tertiary menu accessed from RoleListMenu.
 */
public class RolePermissionEditMenu extends BaseStaffListMenu<RolePermissionEditMenu.Permission> {

    // Placeholder Permission class
    public static class Permission {
        private final String node;
        private boolean enabled;

        public Permission(String node, boolean enabled) {
            this.node = node;
            this.enabled = enabled;
        }

        public String getNode() { return node; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    private final RoleListMenu.Role role;
    private List<Permission> allPermissions = new ArrayList<>();
    private Set<String> enabledPermissions;
    private final String panelUrl;
    private final boolean hasPermission;

    // All available permission nodes
    private static final List<String> AVAILABLE_PERMISSIONS = Arrays.asList(
            "modl.inspect",
            "modl.staff",
            "modl.punish",
            "modl.punish.ban",
            "modl.punish.mute",
            "modl.punish.kick",
            "modl.pardon",
            "modl.reports.view",
            "modl.reports.handle",
            "modl.tickets.view",
            "modl.tickets.respond",
            "modl.notes.create",
            "modl.settings.modify",
            "modl.staff.manage",
            "modl.admin"
    );

    /**
     * Create a new role permission edit menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param role The role to edit
     * @param backAction Action to return to parent menu
     */
    public RolePermissionEditMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                   boolean isAdmin, String panelUrl, RoleListMenu.Role role, Consumer<CirrusPlayerWrapper> backAction) {
        super("Edit: " + role.getName(), platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.role = role;
        this.panelUrl = panelUrl;
        activeTab = StaffTab.SETTINGS;

        // Check permission for role editing
        Cache cache = platform.getCache();
        this.hasPermission = cache != null && cache.hasPermission(viewerUuid, "admin.settings.modify");

        // Initialize permissions
        enabledPermissions = new HashSet<>(role.getPermissions());
        for (String node : AVAILABLE_PERMISSIONS) {
            allPermissions.add(new Permission(node, enabledPermissions.contains(node)));
        }
    }

    @Override
    protected Collection<Permission> elements() {
        // Check permission - return empty if no access
        if (!hasPermission) {
            return Collections.singletonList(new Permission("no_permission", false));
        }
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (allPermissions.isEmpty()) {
            return Collections.singletonList(new Permission(null, false));
        }
        return allPermissions;
    }

    @Override
    protected CirrusItem map(Permission permission) {
        // Handle no permission placeholder
        if ("no_permission".equals(permission.getNode())) {
            return CirrusItem.of(
                    ItemType.BARRIER,
                    ChatElement.ofLegacyText(MenuItems.COLOR_RED + "No Permission"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "You don't have permission",
                            MenuItems.COLOR_GRAY + "to edit role permissions"
                    )
            );
        }
        // Handle placeholder for empty list
        if (permission.getNode() == null) {
            return createEmptyPlaceholder("No permissions");
        }

        return CirrusItem.of(
                permission.isEnabled() ? ItemType.LIME_DYE : ItemType.GRAY_DYE,
                ChatElement.ofLegacyText(
                        (permission.isEnabled() ? MenuItems.COLOR_GREEN : MenuItems.COLOR_GRAY) + permission.getNode()
                ),
                MenuItems.lore(
                        MenuItems.COLOR_YELLOW + "Click to toggle"
                )
        );
    }

    @Override
    protected void handleClick(Click click, Permission permission) {
        // Handle placeholder or no permission - do nothing
        if (permission.getNode() == null || "no_permission".equals(permission.getNode()) || !hasPermission) {
            return;
        }

        // Toggle permission
        permission.setEnabled(!permission.isEnabled());

        if (permission.isEnabled()) {
            enabledPermissions.add(permission.getNode());
        } else {
            enabledPermissions.remove(permission.getNode());
        }

        // TODO: Save to API when endpoint PATCH /v1/panel/roles/{id}/permissions is available
        sendMessage(MenuItems.COLOR_YELLOW + "Permission " + permission.getNode() + " " +
                (permission.isEnabled() ? MenuItems.COLOR_GREEN + "enabled" : MenuItems.COLOR_RED + "disabled"));
        sendMessage(MenuItems.COLOR_GRAY + "(Changes not saved - endpoint needed)");

        // Refresh menu - preserve backAction
        ActionHandlers.openMenu(
                new RolePermissionEditMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, role, backAction))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

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

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));
    }
}
