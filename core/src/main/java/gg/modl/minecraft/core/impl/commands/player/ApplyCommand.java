package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class ApplyCommand extends BaseCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @CommandAlias("%cmd_apply")
    @Description("Submit a staff application")
    @Conditions("player")
    public void staffApplication(CommandIssuer sender) {
        if (ticketUtil.checkCooldown(sender, "staff", localeManager)) return;

        AbstractPlayer applicant = platform.getAbstractPlayer(sender.getUniqueId(), false);
        String createdServer = platform.getPlayerServer(sender.getUniqueId());

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

        ticketUtil.submitUnfinishedTicket(sender, httpClient, platform, localeManager, panelUrl, request, "Staff application", "staff");
    }
}
