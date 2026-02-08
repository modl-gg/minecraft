package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Role List Menu - displays all roles for editing.
 * Secondary menu accessed from Settings.
 */
public class RoleListMenu extends BaseStaffListMenu<RoleListMenu.Role> {

    public static class Role {
        private final String id;
        private final String name;
        private final String description;
        private final List<String> permissions;

        public Role(String id, String name, String description, List<String> permissions) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.permissions = permissions;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<String> getPermissions() { return permissions; }
    }

    private List<Role> roles = new ArrayList<>();
    private final String panelUrl;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;
    private final boolean hasPermission;

    /**
     * Create a new role list menu.
     */
    public RoleListMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                        boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Edit Roles", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        activeTab = StaffTab.SETTINGS;

        Cache cache = platform.getCache();
        this.hasPermission = cache != null && cache.hasPermission(viewerUuid, "admin.settings.modify");

        if (hasPermission) {
            fetchRoles();
        }
    }

    private void fetchRoles() {
        httpClient.getRoles().thenAccept(response -> {
            if (response != null && response.getRoles() != null) {
                roles.clear();
                for (RolesListResponse.RoleEntry entry : response.getRoles()) {
                    // Hide Super Admin role from the list
                    if ("Super Admin".equals(entry.getName())) {
                        continue;
                    }
                    roles.add(new Role(
                            entry.getId(),
                            entry.getName(),
                            entry.getDescription(),
                            entry.getPermissions() != null ? entry.getPermissions() : Collections.emptyList()
                    ));
                }
            }
        }).exceptionally(e -> null);
    }

    @Override
    protected Collection<Role> elements() {
        if (!hasPermission) {
            return Collections.singletonList(new Role("no_permission", null, null, Collections.emptyList()));
        }
        if (roles.isEmpty()) {
            return Collections.singletonList(new Role(null, null, null, Collections.emptyList()));
        }
        return roles;
    }

    @Override
    protected CirrusItem map(Role role) {
        if ("no_permission".equals(role.getId())) {
            return CirrusItem.of(
                    CirrusItemType.BARRIER,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "No Permission"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "You don't have permission",
                            MenuItems.COLOR_GRAY + "to edit roles"
                    )
            );
        }
        if (role.getId() == null) {
            return createEmptyPlaceholder("No roles");
        }

        List<String> lore = new ArrayList<>();

        if (role.getDescription() != null && !role.getDescription().isEmpty()) {
            lore.add(MenuItems.COLOR_WHITE + role.getDescription());
            lore.add("");
        }

        lore.add(MenuItems.COLOR_GRAY + "Permissions:");
        if (role.getPermissions().isEmpty()) {
            lore.add(MenuItems.COLOR_DARK_GRAY + "  (No permissions)");
        } else {
            for (String perm : role.getPermissions()) {
                lore.add(MenuItems.COLOR_WHITE + "  - " + perm);
            }
        }
        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Click to edit permissions");

        return CirrusItem.of(
            CirrusItemType.PAPER,
            CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + role.getName()),
                MenuItems.lore(lore)
        );
    }

    @Override
    protected void handleClick(Click click, Role role) {
        if (role.getId() == null || "no_permission".equals(role.getId()) || !hasPermission) {
            return;
        }

        // Prevent editing own role
        Cache cache = platform.getCache();
        if (cache != null) {
            String viewerRole = cache.getStaffRole(viewerUuid);
            if (viewerRole != null && viewerRole.equals(role.getName())) {
                sendMessage(MenuItems.COLOR_RED + "You cannot edit your own role.");
                return;
            }
        }

        // Open role permission edit menu - back action re-fetches roles
        ActionHandlers.openMenu(
                new RolePermissionEditMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, role,
                        player -> new RoleListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction).display(player)))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

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
