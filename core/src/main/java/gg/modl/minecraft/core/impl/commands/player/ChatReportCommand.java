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
import gg.modl.minecraft.core.service.ChatMessageCache;
import lombok.RequiredArgsConstructor;

import java.util.List;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@RequiredArgsConstructor
public class ChatReportCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final ChatMessageCache chatMessageCache;
    private final TicketCommandUtil ticketUtil;

    @Command("chatreport")
    @Description("Report a player for chat violations (automatically includes recent chat logs)")
    @PlayerOnly
    public void chatReport(CommandActor actor, AbstractPlayer targetPlayer) {
        if (ticketUtil.checkCooldown(actor, "chat", localeManager)) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(actor.uniqueId(), false);

        if (ticketUtil.denySelfReport(actor, reporter, targetPlayer, localeManager)) return;

        String chatLog = chatMessageCache.getChatLogForReport(
            targetPlayer.getUuid().toString(),
            reporter.getUuid().toString()
        );

        if (chatLog.isEmpty()) {
            actor.reply(localeManager.getMessage("messages.no_chat_logs_available", mapOf("player", targetPlayer.getUsername())));
            return;
        }

        String description = "**Chat Report for " + targetPlayer.getUsername() + "**\n\n" +
                             "Reported by: " + reporter.getUsername() + "\n\n" +
                             "**Chat Log:**\n```\n" + chatLog + "\n```";

        String createdServer = platform.getPlayerServer(actor.uniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.getUuid().toString(),
            "chat",
            reporter.getUsername(),
            "Chat Report: " + targetPlayer.getUsername(),
            description,
            targetPlayer.getUuid().toString(),
            targetPlayer.getUsername(),
            "normal",
            createdServer,
            listOf(chatLog.split("\n")),
            listOf()
        );

        ticketUtil.submitFinishedTicket(actor, httpClient, platform, localeManager, panelUrl, request, "Chat report", "chat");
    }
}
