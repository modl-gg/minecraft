package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.RolesListResponse;
import gg.modl.minecraft.api.http.response.StaffListResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Staff List Menu - displays all staff members for role management.
 * Secondary menu accessed from Settings.
 */
public class StaffListMenu extends BaseStaffListMenu<StaffListMenu.StaffMember> {

    public static class StaffMember {
        private final String id;
        private final UUID uuid;
        private final String username;
        private final String currentRole;

        public StaffMember(String id, UUID uuid, String username, String currentRole) {
            this.id = id;
            this.uuid = uuid;
            this.username = username;
            this.currentRole = currentRole;
        }

        public String getId() { return id; }
        public UUID getUuid() { return uuid; }
        public String getUsername() { return username; }
        public String getCurrentRole() { return currentRole; }
    }

    private List<StaffMember> staffMembers = new ArrayList<>();
    private List<String> availableRoles = new ArrayList<>();
    // Role name → order (0 = highest/Super Admin)
    private Map<String, Integer> roleOrders = new HashMap<>();
    private final String panelUrl;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;
    private final boolean hasPermission;
    private String viewerRole;

    // Track selected role for each staff member
    private final Map<String, String> selectedRoles = new HashMap<>();

    private static final String SUPER_ADMIN_ROLE = "Super Admin";

    /**
     * Create a new staff list menu.
     */
    public StaffListMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Manage Staff", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        activeTab = StaffTab.SETTINGS;

        Cache cache = platform.getCache();
        this.hasPermission = cache != null && cache.hasPermission(viewerUuid, "admin.staff.manage");

        if (hasPermission) {
            fetchStaffAndRoles();
        }
    }

    /**
     * Internal constructor for refreshing with preserved state.
     */
    private StaffListMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                          boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction,
                          List<StaffMember> existingStaff, List<String> existingRoles,
                          Map<String, Integer> existingRoleOrders, String viewerRole,
                          Map<String, String> existingSelections) {
        super("Manage Staff", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        activeTab = StaffTab.SETTINGS;

        Cache cache = platform.getCache();
        this.hasPermission = cache != null && cache.hasPermission(viewerUuid, "admin.staff.manage");

        if (existingStaff != null) this.staffMembers = new ArrayList<>(existingStaff);
        if (existingRoles != null) this.availableRoles = new ArrayList<>(existingRoles);
        if (existingRoleOrders != null) this.roleOrders = new HashMap<>(existingRoleOrders);
        this.viewerRole = viewerRole;
        if (existingSelections != null) this.selectedRoles.putAll(existingSelections);
    }

    private void fetchStaffAndRoles() {
        // Fetch roles first to build the order map
        httpClient.getRoles().thenAccept(response -> {
            if (response != null && response.getRoles() != null) {
                availableRoles.clear();
                roleOrders.clear();
                for (RolesListResponse.RoleEntry role : response.getRoles()) {
                    roleOrders.put(role.getName(), role.getOrder());
                    // Don't include Super Admin as an assignable role
                    if (!SUPER_ADMIN_ROLE.equals(role.getName())) {
                        availableRoles.add(role.getName());
                    }
                }
            }
        }).exceptionally(e -> null);

        httpClient.getStaffList().thenAccept(response -> {
            if (response != null && response.getStaff() != null) {
                staffMembers.clear();
                for (StaffListResponse.StaffEntry entry : response.getStaff()) {
                    UUID uuid = null;
                    if (entry.getMinecraftUuid() != null) {
                        try {
                            uuid = UUID.fromString(entry.getMinecraftUuid());
                        } catch (Exception ignored) {}
                    }
                    String displayName = entry.getMinecraftUsername() != null ? entry.getMinecraftUsername() : entry.getUsername();
                    staffMembers.add(new StaffMember(entry.getId(), uuid, displayName, entry.getRole()));

                    // Identify the viewer's role
                    if (uuid != null && uuid.equals(viewerUuid)) {
                        viewerRole = entry.getRole();
                    }
                }

                // Pre-fetch textures for staff members not in cache
                if (platform.getCache() != null) {
                    for (StaffMember staff : staffMembers) {
                        if (staff.getUuid() != null && platform.getCache().getSkinTexture(staff.getUuid()) == null) {
                            final UUID staffUuid = staff.getUuid();
                            gg.modl.minecraft.core.util.WebPlayer.get(staffUuid).thenAccept(wp -> {
                                if (wp != null && wp.valid() && wp.textureValue() != null) {
                                    platform.getCache().cacheSkinTexture(staffUuid, wp.textureValue());
                                }
                            });
                        }
                    }
                }
            }
        }).exceptionally(e -> null);
    }

    private int getRoleOrder(String roleName) {
        return roleOrders.getOrDefault(roleName, Integer.MAX_VALUE);
    }

    private boolean isSuperAdmin(StaffMember staff) {
        return SUPER_ADMIN_ROLE.equals(staff.getCurrentRole());
    }

    private boolean isViewerSelf(StaffMember staff) {
        return staff.getUuid() != null && staff.getUuid().equals(viewerUuid);
    }

    private boolean canModify(StaffMember staff) {
        if (isSuperAdmin(staff)) return false;
        if (isViewerSelf(staff)) return false;
        if (viewerRole == null) return false;
        int viewerOrder = getRoleOrder(viewerRole);
        int targetOrder = getRoleOrder(staff.getCurrentRole());
        // Lower order = higher rank. Can only modify staff with strictly lower rank (higher order number)
        return viewerOrder < targetOrder;
    }

    @Override
    protected Collection<StaffMember> elements() {
        if (!hasPermission) {
            return Collections.singletonList(new StaffMember("no_permission", null, null, null));
        }
        if (staffMembers.isEmpty()) {
            return Collections.singletonList(new StaffMember(null, null, null, null));
        }
        return staffMembers;
    }

    @Override
    protected CirrusItem map(StaffMember staff) {
        if ("no_permission".equals(staff.getId())) {
            return CirrusItem.of(
                    CirrusItemType.BARRIER,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "No Permission"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "You don't have permission",
                            MenuItems.COLOR_GRAY + "to manage staff members"
                    )
            );
        }
        if (staff.getId() == null) {
            return createEmptyPlaceholder("No staff members");
        }

        List<String> lore = new ArrayList<>();
        boolean canMod = canModify(staff);

        if (!canMod) {
            // Show why they can't modify
            lore.add(MenuItems.COLOR_GRAY + "Role: " + MenuItems.COLOR_WHITE + staff.getCurrentRole());
            lore.add("");
            if (isSuperAdmin(staff)) {
                lore.add(MenuItems.COLOR_RED + "Super Admins cannot be modified");
            } else if (isViewerSelf(staff)) {
                lore.add(MenuItems.COLOR_RED + "You cannot modify your own role");
            } else {
                lore.add(MenuItems.COLOR_RED + "You cannot modify staff with");
                lore.add(MenuItems.COLOR_RED + "the same or higher rank");
            }
        } else {
            String selectedRole = selectedRoles.getOrDefault(staff.getId(), staff.getCurrentRole());

            lore.add(MenuItems.COLOR_GRAY + "Roles:");
            for (String role : availableRoles) {
                if (role.equals(staff.getCurrentRole())) {
                    lore.add(MenuItems.COLOR_GREEN + "  \u00a7l" + role + " \u00a7r\u00a77(current)");
                } else if (role.equals(selectedRole) && !role.equals(staff.getCurrentRole())) {
                    lore.add(MenuItems.COLOR_GREEN + "  " + role + " \u00a77(selected)");
                } else {
                    lore.add(MenuItems.COLOR_GRAY + "  " + role);
                }
            }
            lore.add("");
            lore.add(MenuItems.COLOR_YELLOW + "Right-click to cycle roles");
            lore.add(MenuItems.COLOR_YELLOW + "Left-click to apply selected role");
        }

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(
                        (canMod ? MenuItems.COLOR_GOLD : MenuItems.COLOR_GRAY) + staff.getUsername()),
                MenuItems.lore(lore)
        );

        // Apply skin texture from cache if available
        if (staff.getUuid() != null && platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(staff.getUuid());
            if (cachedTexture != null) {
                headItem = headItem.texture(cachedTexture);
            } else {
                // Async fire-and-forget to populate cache for next menu open
                final UUID uuid = staff.getUuid();
                gg.modl.minecraft.core.util.WebPlayer.get(uuid).thenAccept(wp -> {
                    if (wp != null && wp.valid() && wp.textureValue() != null) {
                        platform.getCache().cacheSkinTexture(uuid, wp.textureValue());
                    }
                });
            }
        }

        return headItem;
    }

    @Override
    protected void handleClick(Click click, StaffMember staff) {
        if (staff.getId() == null || "no_permission".equals(staff.getId()) || !hasPermission) {
            return;
        }

        if (!canModify(staff)) {
            return;
        }

        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            cycleRole(click, staff);
        } else {
            applyRole(click, staff);
        }
    }

    private void cycleRole(Click click, StaffMember staff) {
        if (availableRoles.isEmpty()) {
            sendMessage(MenuItems.COLOR_RED + "No roles available.");
            return;
        }

        String currentSelected = selectedRoles.getOrDefault(staff.getId(), staff.getCurrentRole());
        int currentIndex = availableRoles.indexOf(currentSelected);
        int nextIndex = (currentIndex + 1) % availableRoles.size();
        String nextRole = availableRoles.get(nextIndex);

        selectedRoles.put(staff.getId(), nextRole);
        sendMessage(MenuItems.COLOR_YELLOW + "Selected role: " + MenuItems.COLOR_GREEN + nextRole +
                MenuItems.COLOR_GRAY + " (left-click to apply)");

        // Refresh menu - preserve state
        StaffListMenu newMenu = new StaffListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl,
                backAction, staffMembers, availableRoles, roleOrders, viewerRole, selectedRoles);
        ActionHandlers.openMenu(newMenu).handle(click);
    }

    private void applyRole(Click click, StaffMember staff) {
        String selectedRole = selectedRoles.getOrDefault(staff.getId(), staff.getCurrentRole());

        if (selectedRole.equals(staff.getCurrentRole())) {
            sendMessage(MenuItems.COLOR_GRAY + "This is already " + staff.getUsername() + "'s current role.");
            return;
        }

        sendMessage(MenuItems.COLOR_YELLOW + "Applying role " + MenuItems.COLOR_GREEN + selectedRole +
                MenuItems.COLOR_YELLOW + " to " + staff.getUsername() + "...");

        httpClient.updateStaffRole(staff.getId(), selectedRole).thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Role updated successfully!");
            selectedRoles.remove(staff.getId());

            // Refresh staff permissions cache
            httpClient.getStaffPermissions().exceptionally(e -> null);

            // Rebuild menu with fresh data
            StaffListMenu newMenu = new StaffListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction);
            platform.runOnMainThread(() -> ActionHandlers.openMenu(newMenu).handle(click));
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to update role: " + e.getMessage());
            return null;
        });
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
}
