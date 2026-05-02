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
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class StaffListMenu extends BaseStaffListMenu<StaffListMenu.StaffMember> {
    @Getter
    public static class StaffMember {
        private final String id;
        private final UUID uuid;
        private final String username, currentRole;

        public StaffMember(String id, UUID uuid, String username, String currentRole) {
            this.id = id;
            this.uuid = uuid;
            this.username = username;
            this.currentRole = currentRole;
        }

    }

    private static final String SUPER_ADMIN_ROLE = "Super Admin";

    private List<StaffMember> staffMembers = new ArrayList<>();
    private List<String> availableRoles = new ArrayList<>();
    private Map<String, Integer> roleOrders = new HashMap<>();
    private final String panelUrl;
    private String viewerRole;
    private final Map<String, String> selectedRoles = new HashMap<>();
    private final boolean hasPermission;

    public StaffListMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Manage Staff", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        activeTab = StaffTab.SETTINGS;

        Cache cache = platform.getCache();
        this.hasPermission = cache != null && cache.hasPermission(viewerUuid, Permissions.STAFF_MANAGE);

        if (hasPermission)
            fetchStaffAndRoles();
    }

    private StaffListMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                          boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction,
                          List<StaffMember> existingStaff, List<String> existingRoles,
                          Map<String, Integer> existingRoleOrders, String viewerRole,
                          Map<String, String> existingSelections) {
        super("Manage Staff", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        activeTab = StaffTab.SETTINGS;

        Cache cache = platform.getCache();
        this.hasPermission = cache != null && cache.hasPermission(viewerUuid, Permissions.STAFF_MANAGE);

        if (existingStaff != null) this.staffMembers = new ArrayList<>(existingStaff);
        if (existingRoles != null) this.availableRoles = new ArrayList<>(existingRoles);
        if (existingRoleOrders != null) this.roleOrders = new HashMap<>(existingRoleOrders);
        this.viewerRole = viewerRole;
        if (existingSelections != null) this.selectedRoles.putAll(existingSelections);
    }

    private void fetchStaffAndRoles() {
        httpClient.getRoles().thenAccept(response -> {
            if (response != null && response.getRoles() != null) {
                availableRoles.clear();
                roleOrders.clear();
                for (RolesListResponse.RoleEntry role : response.getRoles()) {
                    roleOrders.put(role.getName(), role.getOrder());
                    if (!SUPER_ADMIN_ROLE.equals(role.getName()))
                        availableRoles.add(role.getName());
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

                    if (uuid != null && uuid.equals(viewerUuid)) {
                        viewerRole = entry.getRole();
                    }
                }

                if (platform.getCache() != null) {
                    for (StaffMember staff : staffMembers) {
                        if (staff.getUuid() != null && platform.getCache().getSkinTexture(staff.getUuid()) == null) {
                            final UUID staffUuid = staff.getUuid();
                            WebPlayer.get(staffUuid).thenAccept(wp -> {
                                if (wp != null && wp.isValid() && wp.getTextureValue() != null) {
                                    platform.getCache().cacheSkinTexture(staffUuid, wp.getTextureValue());
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
        return viewerOrder < targetOrder;
    }

    @Override
    protected Collection<StaffMember> elements() {
        if (!hasPermission)
            return Collections.singletonList(new StaffMember("no_permission", null, null, null));
        if (staffMembers.isEmpty())
            return Collections.singletonList(new StaffMember(null, null, null, null));
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
        if (staff.getId() == null)
            return createEmptyPlaceholder("No staff members");

        boolean canMod = canModify(staff);
        List<String> lore = canMod ? buildSelectableStaffLore(staff) : buildLockedStaffLore(staff);

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(
                        (canMod ? MenuItems.COLOR_GOLD : MenuItems.COLOR_GRAY) + staff.getUsername()),
                MenuItems.lore(lore)
        );

        if (staff.getUuid() != null && platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(staff.getUuid());
            if (cachedTexture != null) {
                headItem = headItem.texture(cachedTexture);
            } else {
                final UUID uuid = staff.getUuid();
                WebPlayer.get(uuid).thenAccept(wp -> {
                    if (wp != null && wp.isValid() && wp.getTextureValue() != null) {
                        platform.getCache().cacheSkinTexture(uuid, wp.getTextureValue());
                    }
                });
            }
        }

        return headItem;
    }

    private List<String> buildLockedStaffLore(StaffMember staff) {
        List<String> lore = new ArrayList<>();
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
        return lore;
    }

    private List<String> buildSelectableStaffLore(StaffMember staff) {
        List<String> lore = new ArrayList<>();
        String selectedRole = selectedRoles.getOrDefault(staff.getId(), staff.getCurrentRole());

        lore.add(MenuItems.COLOR_GRAY + "Roles:");
        for (String role : availableRoles) {
            if (role.equals(staff.getCurrentRole())) {
                lore.add(MenuItems.COLOR_GREEN + "  §l" + role + " §r§7(current)");
            } else if (role.equals(selectedRole) && !role.equals(staff.getCurrentRole())) {
                lore.add(MenuItems.COLOR_GREEN + "  " + role + " §7(selected)");
            } else {
                lore.add(MenuItems.COLOR_GRAY + "  " + role);
            }
        }
        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Right-click to cycle roles");
        lore.add(MenuItems.COLOR_YELLOW + "Left-click to apply selected role");
        return lore;
    }

    @Override
    protected void handleClick(Click click, StaffMember staff) {
        if (staff.getId() == null || "no_permission".equals(staff.getId()) || !hasPermission) {
            return;
        }

        if (!canModify(staff)) {
            return;
        }

        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK))
            cycleRole(click, staff);
        else
            applyRole(click, staff);
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

            httpClient.getStaffPermissions().exceptionally(e -> null);

            StaffListMenu newMenu = new StaffListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction);
            ActionHandlers.openMenu(newMenu).handle(click);
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to update role: " + e.getMessage());
            return null;
        });
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);
    }
}
