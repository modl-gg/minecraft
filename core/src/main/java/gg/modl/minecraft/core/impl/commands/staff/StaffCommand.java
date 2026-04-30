package gg.modl.minecraft.core.impl.commands.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.staff.StaffMenu;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;

@RequiredArgsConstructor
public class StaffCommand {
    private final AsyncCommandExecutor commandExecutor;
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final String panelUrl;

    @Command("staffmenu")
    @Description("Open the staff menu")
    @PlayerOnly @StaffOnly
    public void staff(CommandActor actor) {
        UUID senderUuid = actor.uniqueId();
        boolean isAdmin = cache.hasPermission(senderUuid, Permissions.ADMIN);
        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);

        commandExecutor.execute(() -> {
            StaffMenu menu = new StaffMenu(
                platform, httpClientHolder.getClient(), senderUuid, senderName,
                isAdmin, panelUrl, null
            );
            CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
            menu.display(player);
        });
    }
}
