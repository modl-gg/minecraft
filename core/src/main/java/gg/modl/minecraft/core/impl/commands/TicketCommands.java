package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class TicketCommands extends BaseCommand {
    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final String panelUrl; // e.g., "https://myserver.modl.gg"
    private final LocaleManager localeManager;
    private final ChatMessageCache chatMessageCache;
    
    @CommandAlias("report")
    @CommandCompletion("@players")
    @Description("Report a player with a reason")
    @Syntax("<player> <reason...>")
    @Conditions("player")
    public void report(CommandIssuer sender, AbstractPlayer targetPlayer, String reason) {
        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);
        
        if (targetPlayer.username().equalsIgnoreCase(reporter.username())) {
            sender.sendMessage(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.uuid().toString(),
            reporter.username(),
            "player",
            "Player Report: " + targetPlayer.username(),
            "Reported player: " + targetPlayer.username() + "\nReason: " + reason,
            targetPlayer.uuid().toString(),
            targetPlayer.username(),
            null, // no chat logs for general reports
            List.of("report"),
            "normal"
        );
        
        submitFinishedTicket(sender, request, "Report");
    }
    
    @CommandAlias("chatreport")
    @CommandCompletion("@players")
    @Description("Report a player for chat violations (automatically includes recent chat logs)")
    @Syntax("<player>")
    @Conditions("player")
    public void chatReport(CommandIssuer sender, AbstractPlayer targetPlayer) {
        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);
        
        if (targetPlayer.username().equalsIgnoreCase(reporter.username())) {
            sender.sendMessage(localeManager.getMessage("messages.cannot_report_self"));
            return;
        }
        
        // Get the last 30 chat messages from the server where the reporter is located
        List<String> chatLogs = chatMessageCache.getRecentMessages(reporter.uuid().toString(), 30);
        
        // If no messages are cached, show error and return
        if (chatLogs.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("messages.no_chat_logs_available", Map.of("player", targetPlayer.username())));
            return;
        }

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.uuid().toString(),
            reporter.username(),
            "chat",
            "Chat Report: " + targetPlayer.username(),
            "Chat violation report for: " + targetPlayer.username() + "\nAutomatic chat log capture included.",
            targetPlayer.uuid().toString(),
            targetPlayer.username(),
            chatLogs,
            List.of(),
            "normal"
        );
        
        submitFinishedTicket(sender, request, "Chat report");
    }
    
    @CommandAlias("apply")
    @Description("Submit a staff application")
    @Conditions("player")
    public void staffApplication(CommandIssuer sender) {
        AbstractPlayer applicant = platform.getAbstractPlayer(sender.getUniqueId(), false);
        
        CreateTicketRequest request = new CreateTicketRequest(
            applicant.uuid().toString(),
            applicant.username(),
            "staff",
            "Application: " + applicant.username(),
            null, // description will be filled in form
            null,
            null,
            null,
            List.of(),
            "normal"
        );
        
        submitUnfinishedTicket(sender, request, "Staff application");
    }
    
    @CommandAlias("bugreport")
    @Description("Report a bug")
    @Syntax("<description...>")
    @Conditions("player")
    public void bugReport(CommandIssuer sender, String description) {
        AbstractPlayer reporter = platform.getAbstractPlayer(sender.getUniqueId(), false);

        CreateTicketRequest request = new CreateTicketRequest(
            reporter.uuid().toString(),
            reporter.username(),
            "bug",
            "Bug Report: " + reporter.username(),
            description,
            null,
            null,
            null,
            List.of(),
            "normal"
        );
        
        submitUnfinishedTicket(sender, request, "Bug report");
    }
    
    @CommandAlias("support")
    @Description("Request support")
    @Syntax("<description...>")
    @Conditions("player")
    public void supportRequest(CommandIssuer sender, String description) {
        AbstractPlayer requester = platform.getAbstractPlayer(sender.getUniqueId(), false);
        
        CreateTicketRequest request = new CreateTicketRequest(
            requester.uuid().toString(),
            requester.username(),
            "support",
            "Support Request: " + requester.username(),
            description,
            null,
            null,
            null,
            List.of(),
            "normal"
        );
        
        submitUnfinishedTicket(sender, request, "Support request");
    }
    
    private void submitFinishedTicket(CommandIssuer sender, CreateTicketRequest request, String ticketType) {
        sender.sendMessage(localeManager.getMessage("messages.submitting", Map.of("type", ticketType.toLowerCase())));
        
        CompletableFuture<CreateTicketResponse> future = httpClient.createTicket(request);
        
        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                sender.sendMessage(localeManager.getMessage("messages.success", Map.of("type", ticketType)));
                sender.sendMessage(localeManager.getMessage("messages.ticket_id", Map.of("ticketId", response.getTicketId())));
                
                String ticketUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(sender, localeManager.getMessage("messages.view_ticket_label"), ticketUrl, response.getTicketId());
                sender.sendMessage(localeManager.getMessage("messages.evidence_note"));
            } else {
                String error = response.getMessage() != null ? response.getMessage() : localeManager.getMessage("messages.unknown_error");
                sender.sendMessage(localeManager.getMessage("messages.failed_submit", Map.of("type", ticketType.toLowerCase(), "error", error)));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage(localeManager.getMessage("messages.failed_submit", Map.of("type", ticketType.toLowerCase(), "error", throwable.getMessage())));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
            return null;
        });
    }
    
    private void submitUnfinishedTicket(CommandIssuer sender, CreateTicketRequest request, String ticketType) {
        sender.sendMessage(localeManager.getMessage("messages.creating", Map.of("type", ticketType.toLowerCase())));
        
        CompletableFuture<CreateTicketResponse> future = httpClient.createUnfinishedTicket(request);
        
        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                sender.sendMessage(localeManager.getMessage("messages.created", Map.of("type", ticketType)));
                sender.sendMessage(localeManager.getMessage("messages.ticket_id", Map.of("ticketId", response.getTicketId())));
                
                String formUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketMessage(sender, localeManager.getMessage("messages.complete_form_label", Map.of("type", ticketType.toLowerCase())), formUrl, response.getTicketId());
            } else {
                String error = response.getMessage() != null ? response.getMessage() : localeManager.getMessage("messages.unknown_error");
                sender.sendMessage(localeManager.getMessage("messages.failed_create", Map.of("type", ticketType.toLowerCase(), "error", error)));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
            } else {
                sender.sendMessage(localeManager.getMessage("messages.failed_create", Map.of("type", ticketType.toLowerCase(), "error", throwable.getMessage())));
                sender.sendMessage(localeManager.getMessage("messages.try_again"));
            }
            return null;
        });
    }
    
    /**
     * Send a clickable ticket message that opens the ticket URL when clicked
     */
    private void sendClickableTicketMessage(CommandIssuer sender, String message, String ticketUrl, String ticketId) {
        if (sender.isPlayer()) {
            // Create clickable JSON message for players
            String clickableMessage = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"📋 \",\"color\":\"gold\"}," +
                "{\"text\":\"%s: \",\"color\":\"gray\"}," +
                "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}",
                message, ticketUrl, ticketId
            );
            
            UUID senderUuid = sender.getUniqueId();
            platform.runOnMainThread(() -> {
                platform.sendJsonMessage(senderUuid, clickableMessage);
            });
        } else {
            // For console, send plain text with URL
            sender.sendMessage(localeManager.getMessage("messages.console_ticket_url", Map.of("message", message, "url", ticketUrl)));
        }
    }
}