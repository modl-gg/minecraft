package gg.modl.minecraft.core.impl.commands.player;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import lombok.RequiredArgsConstructor;

import java.util.List;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class ChatReportCommand extends BaseCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl;
    private final LocaleManager localeManager;
    private final ChatMessageCache chatMessageCache;
    private final TicketCommandUtil ticketUtil;

    @CommandAlias("%cmd_chatreport")
    @CommandCompletion("@players")
    @Description("Report a player for chat violations (automatically includes recent chat logs)")
    @Syntax("<player>")
    @Conditions("player")
    public void chatReport(CommandIssuer sender, AbstractPlayer targetPlayer) {
        if (ticketUtil.checkCooldown(sender, "chat", localeManager)) return;

        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);

        if (targetPlayer.getUsername().equalsIgnoreCase(reporter.getUsername())) {
            sender.sendMessage(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }

        String chatLog = chatMessageCache.getChatLogForReport(
            targetPlayer.getUuid().toString(),
            reporter.getUuid().toString()
        );

        if (chatLog.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("messages.no_chat_logs_available", mapOf("player", targetPlayer.getUsername())));
            return;
        }

        String description = "**Chat Report for " + targetPlayer.getUsername() + "**\n\n" +
                             "Reported by: " + reporter.getUsername() + "\n\n" +
                             "**Chat Log:**\n```\n" + chatLog + "\n```";

        String createdServer = platform.getPlayerServer(sender.getUniqueId());

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

        ticketUtil.submitFinishedTicket(sender, httpClient, platform, localeManager, panelUrl, request, "Chat report", "chat");
    }
}
