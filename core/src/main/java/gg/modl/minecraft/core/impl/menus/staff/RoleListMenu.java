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
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.util.Permissions;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class RoleListMenu extends BaseStaffListMenu<RoleListMenu.Role> {
    @Getter
    public static class Role {
        private final String id, name, description;
        private final List<String> permissions;

        public Role(String id, String name, String description, List<String> permissions) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.permissions = permissions;
        }

    }

    private final List<Role> roles = new ArrayList<>();
    private final String panelUrl;
    private final boolean hasPermission;

    public RoleListMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                        boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Edit Roles", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        activeTab = StaffTab.SETTINGS;

        Cache cache = platform.getCache();
        this.hasPermission = cache != null && cache.hasPermission(viewerUuid, Permissions.SETTINGS_MODIFY);

        if (hasPermission)
            fetchRoles();
    }

    private void fetchRoles() {
        httpClient.getRoles().thenAccept(response -> {
            if (response != null && response.getRoles() != null) {
                roles.clear();
                for (RolesListResponse.RoleEntry entry : response.getRoles()) {
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
        if (!hasPermission)
            return Collections.singletonList(new Role("no_permission", null, null, Collections.emptyList()));
        if (roles.isEmpty())
            return Collections.singletonList(new Role(null, null, null, Collections.emptyList()));
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
        if (role.getId() == null) return createEmptyPlaceholder("No roles");

        List<String> lore = new ArrayList<>();

        if (role.getDescription() != null && !role.getDescription().isEmpty()) {
            lore.add(MenuItems.COLOR_WHITE + role.getDescription());
            lore.add("");
        }

        lore.add(MenuItems.COLOR_GRAY + "Permissions:");
        if (role.getPermissions().isEmpty())
            lore.add(MenuItems.COLOR_DARK_GRAY + "  (No permissions)");
        else
            for (String perm : role.getPermissions())
                lore.add(MenuItems.COLOR_WHITE + "  - " + perm);
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

        Cache cache = platform.getCache();
        if (cache != null) {
            String viewerRole = cache.getStaffRole(viewerUuid);
            if (viewerRole != null && viewerRole.equals(role.getName())) {
                sendMessage(MenuItems.COLOR_RED + "You cannot edit your own role.");
                return;
            }
        }

        ActionHandlers.openMenu(
                new RolePermissionEditMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, role,
                        player -> new RoleListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction).display(player)))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);
    }
}
