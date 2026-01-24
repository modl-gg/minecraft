package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.staff.StaffMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Command to open the Staff Menu GUI.
 */
@RequiredArgsConstructor
public class StaffCommand extends BaseCommand {
    private final ModlHttpClient httpClient;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    @CommandAlias("staff|staffmenu|sm")
    @Description("Open the staff menu")
    public void staff(CommandIssuer sender) {
        // Must be a player to use this command
        if (!sender.isPlayer()) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        // Check if user is staff member
        boolean isStaffMember = cache.isStaffMemberByPermissions(senderUuid);
        if (!isStaffMember) {
            sender.sendMessage(localeManager.getMessage("player_lookup.permission_denied"));
            return;
        }

        // Check if user has admin permissions
        boolean isAdmin = cache.hasPermission(senderUuid, "modl.admin");

        platform.runOnMainThread(() -> {
            // Get sender name
            String senderName = "Staff";
            if (platform.getPlayer(senderUuid) != null) {
                senderName = platform.getPlayer(senderUuid).username();
            }

            // Open the staff menu
            StaffMenu menu = new StaffMenu(
                    platform,
                    httpClient,
                    senderUuid,
                    senderName,
                    isAdmin,
                    panelUrl,
                    null // No parent menu when opened from command
            );

            // Get CirrusPlayerWrapper and display
            CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
            menu.display(player);
        });
    }
}
