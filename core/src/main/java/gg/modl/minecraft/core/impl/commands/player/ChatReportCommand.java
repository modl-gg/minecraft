package gg.modl.minecraft.core.impl.commands.player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.TicketService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@RequiredArgsConstructor
public class ChatReportCommand {
    private final Platform platform;
    private final LocaleManager localeManager;
    private final ChatMessageCache chatMessageCache;
    private final TicketService ticketUtil;

    @Command("chatreport")
    @Description("Report a player for chat violations (automatically includes recent chat logs)")
    @PlayerOnly
    public void chatReport(CommandActor actor, AbstractPlayer targetPlayer) {
        if (ticketUtil.checkCooldown(actor, "chat")) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(actor.uniqueId(), false);

        if (reporter == null) {
            actor.reply(localeManager.getMessage("general.player_not_found"));
            return;
        }

        if (ticketUtil.denySelfReport(actor, reporter, targetPlayer)) return;

        String chatLog = chatMessageCache.getChatLogForReport(targetPlayer.getUuid().toString());

        if (chatLog.isEmpty()) {
            actor.reply(localeManager.getMessage("messages.no_chat_logs_available", mapOf("player", targetPlayer.getUsername())));
            return;
        }

        String description = "**Chat Report for " + targetPlayer.getUsername() + "**\n\n" +
                             "Reported by: " + reporter.getUsername() + "\n\n" +
                             "**Chat Log:**\n```\n" + chatLog + "\n```";

        String createdServer = platform.getPlayerServer(actor.uniqueId());

        CreateTicketRequest request = CreateTicketRequest.builder()
            .creatorUuid(reporter.getUuid().toString())
            .type("chat")
            .creatorName(reporter.getUsername())
            .subject("Chat Report: " + targetPlayer.getUsername())
            .description(description)
            .reportedPlayerUuid(targetPlayer.getUuid().toString())
            .reportedPlayerName(targetPlayer.getUsername())
            .priority("normal")
            .createdServer(createdServer)
            .chatMessages(listOf(chatLog.split("\n")))
            .tags(listOf())
            .build();

        ticketUtil.submitFinishedTicket(actor, request, "Chat report", "chat");
    }
}
