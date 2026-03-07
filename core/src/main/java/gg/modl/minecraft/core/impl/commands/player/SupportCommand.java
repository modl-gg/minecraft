package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class SupportCommand extends BaseCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @CommandAlias("%cmd_support")
    @Description("Request support")
    @Syntax("<title...>")
    @Conditions("player")
    public void supportRequest(CommandIssuer sender, String title) {
        if (ticketUtil.checkCooldown(sender, "support", localeManager)) return;

        AbstractPlayer requester = platform.getAbstractPlayer(sender.getUniqueId(), false);
        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            requester.uuid().toString(),
            "support",
            requester.username(),
            title,
            null, null, null,
            "normal",
            createdServer,
            null,
            List.of()
        );

        ticketUtil.submitUnfinishedTicket(sender, httpClient, platform, localeManager, panelUrl, request, "Support request", "support");
    }
}
