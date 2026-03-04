package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.PermissionUtil;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Command to list all online staff members with their roles and servers.
 */
@RequiredArgsConstructor
public class StaffListCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandAlias("%cmd_stafflist")
    @Description("List all online staff members")
    @Conditions("staff")
    public void staffList(CommandIssuer sender) {
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

                staffEntries.add(new StaffEntry(displayName, inGameName, role, server));
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
                sender.sendMessage(localeManager.getMessage("staff_list.entry", Map.of(
                        "role", entry.role,
                        "player", entry.displayName,
                        "in-game-name", entry.inGameName,
                        "server", entry.server
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

        StaffEntry(String displayName, String inGameName, String role, String server) {
            this.displayName = displayName;
            this.inGameName = inGameName;
            this.role = role;
            this.server = server;
        }
    }
}
