package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ReplayService;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class HackReportCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final TicketCommandUtil ticketUtil;

    @Command("hackreport")
    @Description("Report a player for cheating/hacking")
    @PlayerOnly
    public void hackReport(CommandActor actor, @Named("player") String targetName, @Optional @ConsumeRemaining String details) {
        if (ticketUtil.checkCooldown(actor, "player", localeManager)) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(actor.uniqueId(), false);
        AbstractPlayer targetPlayer = platform.getAbstractPlayer(targetName, false);

        if (targetPlayer == null) {
            actor.reply(localeManager.getMessage("general.player_not_found"));
            return;
        }

        if (targetPlayer.getUsername().equalsIgnoreCase(reporter.getUsername())) {
            actor.reply(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }

        String description = details != null && !details.isEmpty() ? details : null;
        String createdServer = platform.getPlayerServer(actor.uniqueId());

        ReplayService replayService = platform.getReplayService();
        CompletableFuture<String> replayFuture;
        if (replayService != null && replayService.isReplayAvailable(targetPlayer.getUuid())) {
            replayFuture = replayService.captureReplay(targetPlayer.getUuid(), targetPlayer.getUsername());
        } else {
            replayFuture = CompletableFuture.completedFuture(null);
        }

        replayFuture.whenComplete((replayUrl, replayEx) -> {
            if (replayEx != null) replayUrl = null;

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
                listOf("report", "cheating"),
                replayUrl
            );

            ticketUtil.submitFinishedTicket(actor, httpClient, platform, localeManager, panelUrl, request, "Report", "player");
        });
    }
}
