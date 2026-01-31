package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.staff.StaffMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.MenuUtil;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Command to open the Staff Menu GUI.
 */
@RequiredArgsConstructor
public class StaffCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final String panelUrl;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandAlias("staff|staffmenu|sm")
    @Description("Open the staff menu")
    @Conditions("player|staff")
    public void staff(CommandIssuer sender) {
        // Menus require V2 API
        if (httpClientHolder.getApiVersion() == ApiVersion.V1) {
            sender.sendMessage(localeManager.getMessage("api_errors.menus_require_v2"));
            return;
        }

        // Check if Protocolize is available (required for menus on BungeeCord)
        if (!MenuUtil.isProtocolizeAvailable()) {
            sender.sendMessage(MenuUtil.getMenuUnavailableMessage());
            return;
        }

        UUID senderUuid = sender.getUniqueId();

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
                    getHttpClient(),
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
