package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandler;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.util.Permissions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class RolePermissionEditMenu extends BaseStaffListMenu<RolePermissionEditMenu.Permission> {
    @Getter @AllArgsConstructor
    public static class Permission {
        private final String node;
        @Setter private boolean enabled;
    }

    private final RoleListMenu.Role role;
    private final List<Permission> allPermissions = new ArrayList<>();
    private final Set<String> enabledPermissions, originalPermissions;
    private final String panelUrl;
    private final boolean hasPermission;

    private static final List<String> AVAILABLE_PERMISSIONS = Arrays.asList(
            Permissions.SETTINGS_VIEW,
            Permissions.SETTINGS_VIEW_BILLING,
            Permissions.SETTINGS_MODIFY,
            Permissions.SETTINGS_MODIFY_PUNISHMENTS,
            Permissions.SETTINGS_MODIFY_CONTENT,
            Permissions.SETTINGS_MODIFY_DOMAIN,
            Permissions.SETTINGS_MODIFY_BILLING,
            Permissions.SETTINGS_MODIFY_MIGRATION,
            Permissions.SETTINGS_MODIFY_STORAGE,
            Permissions.STAFF_MANAGE,
            Permissions.STAFF_MANAGE_MEMBERS,
            Permissions.STAFF_MANAGE_ROLES,
            Permissions.AUDIT_VIEW,
            Permissions.AUDIT_VIEW_DASHBOARD,
            Permissions.AUDIT_VIEW_ANALYTICS,
            Permissions.AUDIT_VIEW_LOGS,
            Permissions.PUNISHMENT_MODIFY,
            Permissions.PUNISHMENT_MODIFY_PARDON,
            Permissions.PUNISHMENT_MODIFY_DURATION,
            Permissions.PUNISHMENT_MODIFY_NOTE,
            Permissions.PUNISHMENT_MODIFY_EVIDENCE,
            Permissions.PUNISHMENT_MODIFY_OPTIONS,
            Permissions.CHAT_TOGGLE,
            Permissions.CHAT_CLEAR,
            Permissions.CHAT_SLOW,
            Permissions.MAINTENANCE,
            Permissions.MOD_ACTIONS,
            Permissions.INTERCEPT,
            Permissions.CHAT_LOGS,
            Permissions.COMMAND_LOGS,
            Permissions.TICKET_VIEW_ALL,
            Permissions.TICKET_VIEW_ALL_NOTES,
            Permissions.TICKET_REPLY_ALL,
            Permissions.TICKET_REPLY_ALL_NOTES,
            Permissions.TICKET_CLOSE_ALL,
            Permissions.TICKET_CLOSE_ALL_LOCK,
            Permissions.TICKET_MANAGE,
            Permissions.TICKET_MANAGE_TAGS,
            Permissions.TICKET_MANAGE_HIDE,
            Permissions.TICKET_MANAGE_SUBSCRIBE,
            Permissions.TICKET_DELETE_ALL
    );

    public RolePermissionEditMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                   boolean isAdmin, String panelUrl, RoleListMenu.Role role, Consumer<CirrusPlayerWrapper> backAction) {
        super("Edit: " + role.getName(), platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.role = role;
        this.panelUrl = panelUrl;
        activeTab = StaffTab.SETTINGS;

        Cache cache = platform.getCache();
        boolean isSuperAdmin = "Super Admin".equals(role.getName());
        this.hasPermission = !isSuperAdmin && cache != null && cache.hasPermission(viewerUuid, Permissions.SETTINGS_MODIFY);

        enabledPermissions = new HashSet<>(role.getPermissions());
        originalPermissions = new HashSet<>(role.getPermissions());

        Set<String> allNodes = new LinkedHashSet<>(AVAILABLE_PERMISSIONS);
        for (String perm : role.getPermissions())
            if (perm.startsWith("punishment.apply.")) allNodes.add(perm);

        if (cache != null && cache.getCachedPunishmentTypes() != null && cache.getCachedPunishmentTypes().getData() != null)
            for (PunishmentTypesResponse.PunishmentTypeData type : cache.getCachedPunishmentTypes().getData()) {
                String permNode = "punishment.apply." + type.getName().toLowerCase().replace(" ", "-");
                allNodes.add(permNode);
            }

        for (String node : allNodes)
            allPermissions.add(new Permission(node, enabledPermissions.contains(node)));
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        boolean hasChanges = !enabledPermissions.equals(originalPermissions);
        items.put(MenuSlots.FILTER_BUTTON, CirrusItem.of(
                hasChanges ? CirrusItemType.LIME_DYE : CirrusItemType.GRAY_DYE,
                CirrusChatElement.ofLegacyText(
                        hasChanges ? MenuItems.COLOR_GREEN + "Apply Changes" : MenuItems.COLOR_GRAY + "No Changes"),
                MenuItems.lore(
                        hasChanges ?
                                MenuItems.COLOR_YELLOW + "Click to save permission changes" :
                                MenuItems.COLOR_DARK_GRAY + "Toggle permissions then click to save"
                )
        ).actionHandler("applyPermissions"));

        return items;
    }

    @Override
    protected Collection<Permission> elements() {
        if (!hasPermission)
            return Collections.singletonList(new Permission("no_permission", false));
        if (allPermissions.isEmpty())
            return Collections.singletonList(new Permission(null, false));
        return allPermissions;
    }

    private static String getParentNode(String node) {
        int lastDot = node.lastIndexOf('.');
        if (lastDot <= 0) return null;
        String candidate = node.substring(0, lastDot);
        return AVAILABLE_PERMISSIONS.contains(candidate) ? candidate : null;
    }

    private boolean isChildPermission(String node) {
        return getParentNode(node) != null;
    }

    private boolean isParentEnabled(String childNode) {
        String parent = getParentNode(childNode);
        return parent != null && enabledPermissions.contains(parent);
    }

    @Override
    protected CirrusItem map(Permission permission) {
        if ("no_permission".equals(permission.getNode())) return CirrusItem.of(
                CirrusItemType.BARRIER,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "No Permission"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "You don't have permission",
                            MenuItems.COLOR_GRAY + "to edit role permissions"
                    ));

        if (permission.getNode() == null) return createEmptyPlaceholder("No permissions");

        boolean changed = permission.isEnabled() != originalPermissions.contains(permission.getNode());
        String suffix = changed ? MenuItems.COLOR_YELLOW + " *" : "";
        boolean isChild = isChildPermission(permission.getNode());
        String displayPrefix = isChild ? "  ↳ " : "";

        if (isChild && isParentEnabled(permission.getNode())) {
            CirrusItem item = CirrusItem.of(
                    CirrusItemType.LIME_DYE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + displayPrefix + permission.getNode()),
                    MenuItems.lore(
                            MenuItems.COLOR_DARK_GRAY + "Auto-granted by parent"
                    )
            );
            item.glow();
            return item;
        }

        return CirrusItem.of(
            permission.isEnabled() ? CirrusItemType.LIME_DYE : CirrusItemType.GRAY_DYE,
            CirrusChatElement.ofLegacyText(
                (permission.isEnabled() ? MenuItems.COLOR_GREEN : MenuItems.COLOR_GRAY) + displayPrefix + permission.getNode() + suffix
            ),
            MenuItems.lore(MenuItems.COLOR_YELLOW + "Click to toggle")
        );
    }

    @Override
    protected void handleClick(Click click, Permission permission) {
        if (permission.getNode() == null || "no_permission".equals(permission.getNode()) || !hasPermission) return;

        if (isChildPermission(permission.getNode()) && isParentEnabled(permission.getNode())) return;

        boolean newState = !permission.isEnabled();
        permission.setEnabled(newState);

        if (newState) {
            enabledPermissions.add(permission.getNode());
            for (Permission p : allPermissions) {
                String parent = getParentNode(p.getNode());
                if (permission.getNode().equals(parent)) {
                    p.setEnabled(true);
                    enabledPermissions.add(p.getNode());
                }
            }
        } else enabledPermissions.remove(permission.getNode());

        RoleListMenu.Role localRole = new RoleListMenu.Role(
                role.getId(), role.getName(), role.getDescription(), new ArrayList<>(enabledPermissions));
        RolePermissionEditMenu newMenu = new RolePermissionEditMenu(
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, localRole, backAction);
        newMenu.originalPermissions.clear();
        newMenu.originalPermissions.addAll(this.originalPermissions);

        newMenu.allPermissions.clear();
        for (Permission p : this.allPermissions)
            newMenu.allPermissions.add(new Permission(p.getNode(), enabledPermissions.contains(p.getNode())));

        ActionHandlers.openMenu(newMenu).handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("applyPermissions", click -> {
            handleApply(click);
            return CallResult.DENY_GRABBING;
        });

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);
    }

    private void handleApply(Click click) {
        if (enabledPermissions.equals(originalPermissions)) {
            sendMessage(MenuItems.COLOR_GRAY + "No changes to save.");
            return;
        }

        sendMessage(MenuItems.COLOR_YELLOW + "Saving permissions for " + role.getName() + "...");

        httpClient.updateRolePermissions(role.getId(), new ArrayList<>(enabledPermissions)).thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Permissions saved successfully!");
            if (backAction != null) backAction.accept(click.player());
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to save: " + e.getMessage());
            return null;
        });
    }
}
