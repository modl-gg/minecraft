package gg.modl.minecraft.core.impl.commands.player;

import gg.modl.minecraft.core.PluginServices;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.service.TicketService;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ReplayService;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;

@RequiredArgsConstructor
public class HackReportCommand {
    private final Platform platform;
    private final LocaleManager localeManager;
    private final TicketService ticketUtil;

    @Command("hackreport")
    @Description("Report a player for cheating/hacking")
    @PlayerOnly
    public void hackReport(CommandActor actor, @Named("player") String targetName, @Optional @ConsumeRemaining String details) {
        if (ticketUtil.checkCooldown(actor, "player")) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(actor.uniqueId(), false);
        AbstractPlayer targetPlayer = platform.getAbstractPlayer(targetName, false);

        if (reporter == null) {
            actor.reply(localeManager.getMessage("general.player_not_found"));
            return;
        }

        if (targetPlayer == null) {
            actor.reply(localeManager.getMessage("general.player_not_found"));
            return;
        }

        if (ticketUtil.denySelfReport(actor, reporter, targetPlayer)) return;

        String description = details != null && !details.isEmpty() ? details : null;
        String createdServer = platform.getPlayerServer(actor.uniqueId());

        ReplayService replayService = PluginServices.replay();
        CompletableFuture<String> replayFuture;
        if (replayService != null && replayService.shouldAttemptCapture(targetPlayer.getUuid())) {
            replayFuture = replayService.captureReplay(targetPlayer.getUuid(), targetPlayer.getUsername());
        } else {
            replayFuture = CompletableFuture.completedFuture(null);
        }

        replayFuture.whenComplete((replayUrl, replayEx) -> {
            if (replayEx != null) replayUrl = null;

            CreateTicketRequest request = CreateTicketRequest.builder()
                .creatorUuid(reporter.getUuid().toString())
                .type("player")
                .creatorName(reporter.getUsername())
                .subject("Cheating: " + targetPlayer.getUsername())
                .description(description)
                .reportedPlayerUuid(targetPlayer.getUuid().toString())
                .reportedPlayerName(targetPlayer.getUsername())
                .priority("normal")
                .createdServer(createdServer)
                .tags(listOf("report", "cheating"))
                .replayUrl(replayUrl)
                .build();

            ticketUtil.submitFinishedTicket(actor, request, "Report", "player");
        });
    }
}
