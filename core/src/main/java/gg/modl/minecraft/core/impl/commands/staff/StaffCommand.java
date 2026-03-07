package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.staff.StaffMenu;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class StaffCommand extends BaseCommand {
    private final AsyncCommandExecutor commandExecutor;
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final String panelUrl;

    @CommandAlias("%cmd_staffmenu")
    @Description("Open the staff menu")
    @Conditions("player|staff")
    public void staff(CommandIssuer sender) {
        UUID senderUuid = sender.getUniqueId();
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
