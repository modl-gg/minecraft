package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class SupportCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @Command("support")
    @Description("Request support")
    @PlayerOnly
    public void supportRequest(CommandActor actor) {
        if (ticketUtil.checkCooldown(actor, "support", localeManager)) return;

        AbstractPlayer requester = platform.getAbstractPlayer(actor.uniqueId(), false);
        String createdServer = platform.getPlayerServer(actor.uniqueId());

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

        ticketUtil.submitUnfinishedTicket(actor, httpClient, platform, localeManager, panelUrl, request, "Support request", "support");
    }
}
