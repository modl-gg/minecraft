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
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

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

/**
 * Role Permission Edit Menu - edit permissions for a role.
 * Tertiary menu accessed from RoleListMenu.
 * Changes are batched and saved via an Apply button.
 */
public class RolePermissionEditMenu extends BaseStaffListMenu<RolePermissionEditMenu.Permission> {

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
    private final Set<String> originalPermissions;
    private final String panelUrl;
    private final boolean hasPermission;

    // All available static permission nodes
    private static final List<String> AVAILABLE_PERMISSIONS = Arrays.asList(
            // Admin permissions
            "admin.settings.view",
            "admin.settings.modify",
            "admin.staff.manage",
            "admin.audit.view",
            // Punishment permissions
            "punishment.modify",
            "punishment.apply.kick",
            "punishment.apply.manual-mute",
            "punishment.apply.manual-ban",
            "punishment.apply.blacklist",
            // Ticket permissions
            "ticket.view.all",
            "ticket.reply.all",
            "ticket.close.all",
            "ticket.delete.all"
    );

    /**
     * Create a new role permission edit menu.
     */
    public RolePermissionEditMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                   boolean isAdmin, String panelUrl, RoleListMenu.Role role, Consumer<CirrusPlayerWrapper> backAction) {
        super("Edit: " + role.getName(), platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.role = role;
        this.panelUrl = panelUrl;
        activeTab = StaffTab.SETTINGS;

        Cache cache = platform.getCache();
        // Prevent editing the Super Admin role
        boolean isSuperAdmin = "Super Admin".equals(role.getName());
        this.hasPermission = !isSuperAdmin && cache != null && cache.hasPermission(viewerUuid, "admin.settings.modify");

        // Initialize permissions from role data
        enabledPermissions = new HashSet<>(role.getPermissions());
        originalPermissions = new HashSet<>(role.getPermissions());

        // Build full permission list: static permissions + any dynamic punishment permissions from the role
        Set<String> allNodes = new LinkedHashSet<>(AVAILABLE_PERMISSIONS);
        for (String perm : role.getPermissions()) {
            if (perm.startsWith("punishment.apply.")) {
                allNodes.add(perm);
            }
        }
        // Also add dynamic punishment permissions from cached punishment types
        if (cache != null && cache.getCachedPunishmentTypes() != null
                && cache.getCachedPunishmentTypes().getData() != null) {
            for (var type : cache.getCachedPunishmentTypes().getData()) {
                String permNode = "punishment.apply." + type.getName().toLowerCase().replace(" ", "-");
                allNodes.add(permNode);
            }
        }

        for (String node : allNodes) {
            allPermissions.add(new Permission(node, enabledPermissions.contains(node)));
        }
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add Apply button at slot 40 (between prev/next arrows)
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
        if (!hasPermission) {
            return Collections.singletonList(new Permission("no_permission", false));
        }
        if (allPermissions.isEmpty()) {
            return Collections.singletonList(new Permission(null, false));
        }
        return allPermissions;
    }

    @Override
    protected CirrusItem map(Permission permission) {
        if ("no_permission".equals(permission.getNode())) {
            return CirrusItem.of(
                CirrusItemType.BARRIER,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "No Permission"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "You don't have permission",
                            MenuItems.COLOR_GRAY + "to edit role permissions"
                    )
            );
        }
        if (permission.getNode() == null) {
            return createEmptyPlaceholder("No permissions");
        }

        boolean changed = permission.isEnabled() != originalPermissions.contains(permission.getNode());
        String suffix = changed ? MenuItems.COLOR_YELLOW + " *" : "";

        return CirrusItem.of(
                permission.isEnabled() ? CirrusItemType.LIME_DYE : CirrusItemType.GRAY_DYE,
            CirrusChatElement.ofLegacyText(
                        (permission.isEnabled() ? MenuItems.COLOR_GREEN : MenuItems.COLOR_GRAY) + permission.getNode() + suffix
                ),
                MenuItems.lore(
                        MenuItems.COLOR_YELLOW + "Click to toggle"
                )
        );
    }

    @Override
    protected void handleClick(Click click, Permission permission) {
        if (permission.getNode() == null || "no_permission".equals(permission.getNode()) || !hasPermission) {
            return;
        }

        // Toggle permission locally (no API call)
        boolean newState = !permission.isEnabled();
        permission.setEnabled(newState);

        if (newState) {
            enabledPermissions.add(permission.getNode());
        } else {
            enabledPermissions.remove(permission.getNode());
        }

        // Create new menu for fresh render, but preserve permission order
        RoleListMenu.Role localRole = new RoleListMenu.Role(
                role.getId(), role.getName(), role.getDescription(), new ArrayList<>(enabledPermissions));
        RolePermissionEditMenu newMenu = new RolePermissionEditMenu(
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, localRole, backAction);
        newMenu.originalPermissions.clear();
        newMenu.originalPermissions.addAll(this.originalPermissions);

        // Copy permission order from current menu instead of the reconstructed order
        newMenu.allPermissions.clear();
        for (Permission p : this.allPermissions) {
            newMenu.allPermissions.add(new Permission(p.getNode(), enabledPermissions.contains(p.getNode())));
        }

        ActionHandlers.openMenu(newMenu).handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Apply button handler
        registerActionHandler("applyPermissions", (ActionHandler) click -> {
            handleApply(click);
            return CallResult.DENY_GRABBING;
        });

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

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));
    }

    private void handleApply(Click click) {
        if (enabledPermissions.equals(originalPermissions)) {
            sendMessage(MenuItems.COLOR_GRAY + "No changes to save.");
            return;
        }

        sendMessage(MenuItems.COLOR_YELLOW + "Saving permissions for " + role.getName() + "...");

        httpClient.updateRolePermissions(role.getId(), new ArrayList<>(enabledPermissions)).thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Permissions saved successfully!");

            // Go back to previous menu (RoleListMenu)
            if (backAction != null) {
                platform.runOnMainThread(() -> backAction.accept(click.player()));
            }
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to save: " + e.getMessage());
            return null;
        });
    }
}
