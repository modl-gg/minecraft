package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.staff.StaffMembersMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.util.PermissionUtil;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Command to list all online staff members.
 * Opens the Staff Members GUI by default. Use -p flag for a text-based print version.
 */
@RequiredArgsConstructor
public class StaffListCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final VanishService vanishService;
    private final HttpClientHolder httpClientHolder;
    private final String panelUrl;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandAlias("%cmd_stafflist")
    @Description("List all online staff members")
    @Conditions("staff")
    public void staffList(CommandIssuer sender, @Optional String flag) {
        if ("-p".equals(flag)) {
            printStaffList(sender);
            return;
        }

        // Open GUI (requires player)
        if (!sender.isPlayer()) {
            printStaffList(sender);
            return;
        }

        UUID viewerUuid = sender.getUniqueId();
        AbstractPlayer viewer = platform.getAbstractPlayer(viewerUuid, false);
        String viewerName = viewer != null ? viewer.getName() : viewerUuid.toString();
        boolean isAdmin = PermissionUtil.hasAnyPermission(sender, cache,
                "admin.settings.view", "admin.settings.modify");

        StaffMembersMenu menu = new StaffMembersMenu(
                platform, getHttpClient(), viewerUuid, viewerName, isAdmin, panelUrl, null);
        CirrusPlayerWrapper player = platform.getPlayerWrapper(viewerUuid);
        menu.display(player);
    }

    private void printStaffList(CommandIssuer sender) {
        Set<UUID> onlinePlayers = cache.getOnlinePlayers();

        // Collect online staff members
        List<StaffEntry> staffEntries = new ArrayList<>();
        for (UUID uuid : onlinePlayers) {
            if (PermissionUtil.isStaff(uuid, cache)) {
                AbstractPlayer player = platform.getPlayer(uuid);
                String inGameName = player != null ? player.getName() : uuid.toString();

                String displayName = cache.getStaffDisplayName(uuid);
                if (displayName == null) {
                    displayName = inGameName;
                }

                String role = cache.getStaffRole(uuid);
                if (role == null) {
                    role = "Staff";
                }

                String server = platform.getPlayerServer(uuid);
                if (server == null) {
                    server = "Unknown";
                }

                boolean vanished = vanishService.isVanished(uuid);
                staffEntries.add(new StaffEntry(displayName, inGameName, role, server, vanished));
            }
        }

        // Send header
        sender.sendMessage(localeManager.getMessage("staff_list.header", Map.of(
                "count", String.valueOf(staffEntries.size())
        )));

        if (staffEntries.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("staff_list.empty"));
        } else {
            for (StaffEntry entry : staffEntries) {
                String vanishTag = entry.vanished ? localeManager.getMessage("staff_list.vanish") : "";
                sender.sendMessage(localeManager.getMessage("staff_list.entry", Map.of(
                        "role", entry.role,
                        "player", entry.displayName,
                        "in-game-name", entry.inGameName,
                        "server", entry.server,
                        "v", vanishTag
                )));
            }
        }

        // Send footer
        sender.sendMessage(localeManager.getMessage("staff_list.footer", Map.of(
                "count", String.valueOf(staffEntries.size())
        )));
    }

    private static class StaffEntry {
        final String displayName;
        final String inGameName;
        final String role;
        final String server;
        final boolean vanished;

        StaffEntry(String displayName, String inGameName, String role, String server, boolean vanished) {
            this.displayName = displayName;
            this.inGameName = inGameName;
            this.role = role;
            this.server = server;
            this.vanished = vanished;
        }
    }
}
