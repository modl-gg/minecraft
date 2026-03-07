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
import java.util.Map;

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

        if (targetPlayer.username().equalsIgnoreCase(reporter.username())) {
            sender.sendMessage(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }

        String chatLog = chatMessageCache.getChatLogForReport(
            targetPlayer.uuid().toString(),
            reporter.uuid().toString()
        );

        if (chatLog.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("messages.no_chat_logs_available", Map.of("player", targetPlayer.username())));
            return;
        }

        String description = "**Chat Report for " + targetPlayer.username() + "**\n\n" +
                             "Reported by: " + reporter.username() + "\n\n" +
                             "**Chat Log:**\n```\n" + chatLog + "\n```";

        String createdServer = platform.getPlayerServer(sender.getUniqueId());

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.uuid().toString(),
            "chat",
            reporter.username(),
            "Chat Report: " + targetPlayer.username(),
            description,
            targetPlayer.uuid().toString(),
            targetPlayer.username(),
            "normal",
            createdServer,
            List.of(chatLog.split("\n")),
            List.of()
        );

        ticketUtil.submitFinishedTicket(sender, httpClient, platform, localeManager, panelUrl, request, "Chat report", "chat");
    }
}
