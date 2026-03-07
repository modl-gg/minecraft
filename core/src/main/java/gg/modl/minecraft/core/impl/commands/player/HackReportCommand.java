package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class HackReportCommand extends BaseCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @CommandAlias("%cmd_hackreport")
    @CommandCompletion("@players")
    @Description("Report a player for cheating/hacking")
    @Syntax("<player> [details]")
    @Conditions("player")
    public void hackReport(CommandIssuer sender, String targetName, @Optional String details) {
        if (ticketUtil.checkCooldown(sender, "player", localeManager)) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);
        AbstractPlayer targetPlayer = platform.getAbstractPlayer(targetName, false);

        if (targetPlayer == null) {
            sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            return;
        }

        if (targetPlayer.getUsername().equalsIgnoreCase(reporter.getUsername())) {
            sender.sendMessage(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }

        String description = details != null && !details.isEmpty() ? details : null;
        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.getUuid().toString(),
            "player",
            reporter.getUsername(),
            "Cheating: " + targetPlayer.getUsername(),
            description,
            targetPlayer.getUuid().toString(),
            targetPlayer.getUsername(),
            "normal",
            createdServer,
            null,
            List.of("report", "cheating")
        );

        ticketUtil.submitFinishedTicket(sender, httpClient, platform, localeManager, panelUrl, request, "Report", "player");
    }
}
