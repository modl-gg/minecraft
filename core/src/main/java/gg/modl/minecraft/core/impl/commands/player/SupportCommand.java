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

import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class SupportCommand extends BaseCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @CommandAlias("%cmd_support")
    @Description("Request support")
    @Conditions("player")
    public void supportRequest(CommandIssuer sender) {
        if (ticketUtil.checkCooldown(sender, "support", localeManager)) return;

        AbstractPlayer requester = platform.getAbstractPlayer(sender.getUniqueId(), false);
        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            requester.getUuid().toString(),
            "support",
            requester.getUsername(),
            null,
            null, null, null,
            "normal",
            createdServer,
            null,
            listOf()
        );

        ticketUtil.submitUnfinishedTicket(sender, httpClient, platform, localeManager, panelUrl, request, "Support request", "support");
    }
}
