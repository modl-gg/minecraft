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
public class ApplyCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @Command("apply")
    @Description("Submit a staff application")
    @PlayerOnly
    public void staffApplication(CommandActor actor) {
        if (ticketUtil.checkCooldown(actor, "staff", localeManager)) return;

        AbstractPlayer applicant = platform.getAbstractPlayer(actor.uniqueId(), false);
        String createdServer = platform.getPlayerServer(actor.uniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            applicant.getUuid().toString(),
            "staff",
            applicant.getUsername(),
            "Application: " + applicant.getUsername(),
            null, null,
            null,
            "normal",
            createdServer,
            null,
            listOf()
        );

        ticketUtil.submitUnfinishedTicket(actor, httpClient, platform, localeManager, panelUrl, request, "Staff application", "staff");
    }
}
