package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Staff List Menu - displays all staff members for role management.
 * Secondary menu accessed from Settings.
 */
public class StaffListMenu extends BaseStaffListMenu<StaffListMenu.StaffMember> {

    // Placeholder StaffMember class since no endpoint exists yet
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
    private final String panelUrl;
    private final List<String> availableRoles = Arrays.asList("Staff", "Moderator", "Admin", "Owner");
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    // Track selected role for each staff member
    private final Map<String, String> selectedRoles = new java.util.HashMap<>();

    /**
     * Create a new staff list menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param backAction Action to return to parent menu
     */
    public StaffListMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Manage Staff", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        activeTab = StaffTab.SETTINGS;

        // TODO: Fetch staff members when endpoint GET /v1/panel/staff is available
        // For now, list is empty
    }

    @Override
    protected Collection<StaffMember> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (staffMembers.isEmpty()) {
            return Collections.singletonList(new StaffMember(null, null, null, null));
        }
        return staffMembers;
    }

    @Override
    protected CirrusItem map(StaffMember staff) {
        // Handle placeholder for empty list
        if (staff.getId() == null) {
            return createEmptyPlaceholder("No staff members");
        }

        List<String> lore = new ArrayList<>();

        // Get selected role (or current role if none selected)
        String selectedRole = selectedRoles.getOrDefault(staff.getId(), staff.getCurrentRole());

        lore.add(MenuItems.COLOR_GRAY + "Roles:");
        for (String role : availableRoles) {
            if (role.equals(staff.getCurrentRole())) {
                // Current role - bold green
                lore.add(MenuItems.COLOR_GREEN + "  §l" + role + " §r§7(current)");
            } else if (role.equals(selectedRole)) {
                // Selected role - green
                lore.add(MenuItems.COLOR_GREEN + "  " + role + " §7(selected)");
            } else {
                // Other roles - gray
                lore.add(MenuItems.COLOR_GRAY + "  " + role);
            }
        }
        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Right-click to cycle roles");
        lore.add(MenuItems.COLOR_YELLOW + "Left-click to apply selected role");

        return CirrusItem.of(
                ItemType.PLAYER_HEAD,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + staff.getUsername()),
                MenuItems.lore(lore)
        );
    }

    @Override
    protected void handleClick(Click click, StaffMember staff) {
        // Handle placeholder - do nothing
        if (staff.getId() == null) {
            return;
        }

        // In Cirrus, we'd need to check click type
        // For now, treat all clicks as cycle
        cycleRole(click, staff);
    }

    private void cycleRole(Click click, StaffMember staff) {
        String currentSelected = selectedRoles.getOrDefault(staff.getId(), staff.getCurrentRole());
        int currentIndex = availableRoles.indexOf(currentSelected);
        int nextIndex = (currentIndex + 1) % availableRoles.size();
        String nextRole = availableRoles.get(nextIndex);

        selectedRoles.put(staff.getId(), nextRole);
        sendMessage(MenuItems.COLOR_YELLOW + "Selected role: " + MenuItems.COLOR_GREEN + nextRole +
                MenuItems.COLOR_GRAY + " (left-click to apply)");

        // Refresh menu - preserve backAction
        StaffListMenu newMenu = new StaffListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction);
        newMenu.selectedRoles.putAll(this.selectedRoles);
        ActionHandlers.openMenu(newMenu).handle(click);
    }

    private void applyRole(Click click, StaffMember staff) {
        String selectedRole = selectedRoles.getOrDefault(staff.getId(), staff.getCurrentRole());

        // TODO: Apply role when endpoint PATCH /v1/panel/staff/{id}/role is available
        sendMessage(MenuItems.COLOR_YELLOW + "Applying role " + selectedRole + " to " + staff.getUsername() + "...");
        sendMessage(MenuItems.COLOR_GRAY + "(Role change not saved - endpoint needed)");
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
