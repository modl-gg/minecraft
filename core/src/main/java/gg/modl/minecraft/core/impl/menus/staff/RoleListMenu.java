package gg.modl.minecraft.core.impl.menus.staff;

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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Role List Menu - displays all roles for editing.
 * Secondary menu accessed from Settings.
 */
public class RoleListMenu extends BaseStaffListMenu<RoleListMenu.Role> {

    // Placeholder Role class since no endpoint exists yet
    public static class Role {
        private final String id;
        private final String name;
        private final List<String> permissions;

        public Role(String id, String name, List<String> permissions) {
            this.id = id;
            this.name = name;
            this.permissions = permissions;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public List<String> getPermissions() { return permissions; }
    }

    private List<Role> roles = new ArrayList<>();
    private final String panelUrl;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    /**
     * Create a new role list menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param backAction Action to return to parent menu
     */
    public RoleListMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                        boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Edit Roles", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        activeTab = StaffTab.SETTINGS;

        // TODO: Fetch roles when endpoint GET /v1/panel/roles is available
        // For now, list is empty
    }

    @Override
    protected Collection<Role> elements() {
        return roles;
    }

    @Override
    protected CirrusItem map(Role role) {
        List<String> lore = new ArrayList<>();

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
                ItemType.PAPER,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + role.getName()),
                MenuItems.lore(lore)
        );
    }

    @Override
    protected void handleClick(Click click, Role role) {
        // Open role permission edit menu
        click.clickedMenu().close();
        new RolePermissionEditMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, role,
                player -> new RoleListMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction).display(player))
                .display(click.player());
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Override header navigation
        registerActionHandler("openOnlinePlayers", click -> {
            click.clickedMenu().close();
            new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
        registerActionHandler("openReports", click -> {
            click.clickedMenu().close();
            new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
        registerActionHandler("openPunishments", click -> {
            click.clickedMenu().close();
            new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
        registerActionHandler("openTickets", click -> {
            click.clickedMenu().close();
            new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
        registerActionHandler("openPanel", click -> {
            sendMessage("");
            sendMessage(MenuItems.COLOR_GOLD + "Staff Panel:");
            sendMessage(MenuItems.COLOR_AQUA + panelUrl);
            sendMessage("");
        });
        registerActionHandler("openSettings", click -> {
            click.clickedMenu().close();
            new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, parentBackAction)
                    .display(click.player());
        });
    }
}
