package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.actionhandler.ActionHandler;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.text.CirrusChatElement;
import dev.simplix.cirrus.menus.SimpleMenu;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Confirmation menu (3x9 layout).
 * Reviews the report and allows the player to confirm or cancel.
 */
public class ReportConfirmMenu extends SimpleMenu {

    private final AbstractPlayer reporter;
    private final AbstractPlayer target;
    private final ModlHttpClient httpClient;
    private final LocaleManager locale;
    private final Platform platform;
    private final String panelUrl;
    private final ReportGuiConfig guiConfig;
    private final ChatMessageCache chatMessageCache;
    private final ReportData reportData;

    public ReportConfirmMenu(AbstractPlayer reporter, AbstractPlayer target, ModlHttpClient httpClient,
                              LocaleManager locale, Platform platform, String panelUrl,
                              ReportGuiConfig guiConfig, ChatMessageCache chatMessageCache,
                              ReportData reportData) {
        super();
        this.reporter = reporter;
        this.target = target;
        this.httpClient = httpClient;
        this.locale = locale;
        this.platform = platform;
        this.panelUrl = panelUrl;
        this.guiConfig = guiConfig;
        this.chatMessageCache = chatMessageCache;
        this.reportData = reportData;

        title(locale.getMessage("messages.report_gui_title", Map.of("player", target.username())));
        type(CirrusInventoryType.GENERIC_9X3);
        buildMenu();
    }

    private void buildMenu() {
        // Row 1 layout: * * C * P * Q * *
        // C (slot 11): Confirm Report
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(MenuItems.COLOR_GRAY + "Reason: " + MenuItems.COLOR_WHITE + reportData.getReason());
        if (reportData.getDetails() != null) {
            confirmLore.add(MenuItems.COLOR_GRAY + "Details: " + MenuItems.COLOR_WHITE + reportData.getDetails());
        }
        if (reportData.getChatLog() != null) {
            confirmLore.add(MenuItems.COLOR_GRAY + "Chat Log: " + MenuItems.COLOR_WHITE + "Attached");
        }
        confirmLore.add("");
        confirmLore.addAll(locale.getMessageList("messages.report_confirm_lore"));

        set(CirrusItem.of(
                CirrusItemType.of("minecraft:lime_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_confirm")),
                MenuItems.lore(confirmLore)
        ).slot(11).actionHandler("confirm"));

        // P (slot 13): Player skull
        List<String> skullLines = locale.getMessageList("messages.report_skull_confirm", Map.of("player", target.username()));
        set(MenuItems.playerHead(
                target.username(),
                skullLines.get(0),
                skullLines.subList(1, skullLines.size())
        ).slot(13));

        // Q (slot 15): Cancel Report
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:red_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_cancel")),
                MenuItems.lore(locale.getMessageList("messages.report_cancel_lore"))
        ).slot(15).actionHandler("cancel"));

        // Row 2 layout: * * * * B * * * *
        // B (slot 22): Back button
        set(MenuItems.backButton().slot(22));
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("confirm", (ActionHandler) click -> {
            click.clickedMenu().close();
            submitReport();
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("cancel", (ActionHandler) click -> {
            click.clickedMenu().close();
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("back", (ActionHandler) click -> {
            // Return to details menu
            ActionHandlers.openMenu(new ReportDetailsMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache, reportData, null
            )).handle(click);

            return CallResult.DENY_GRABBING;
        });
    }

    private void submitReport() {
        // Build description body
        StringBuilder description = new StringBuilder();
        description.append("Reason: ").append(reportData.getReason());

        if (reportData.getDetails() != null) {
            description.append("\nDetails: ").append(reportData.getDetails());
        }

        if (reportData.getChatLog() != null) {
            description.append("\n\n**Chat Log:**\n```\n").append(reportData.getChatLog()).append("\n```");
        }

        String ticketType = reportData.isChatReport() ? "chat" : "player";
        String subject = (reportData.isChatReport() ? "Chat Report" : "Player Report") + ": " + target.username();

        List<String> chatMessages = null;
        if (reportData.getChatLog() != null) {
            chatMessages = List.of(reportData.getChatLog().split("\n"));
        }

        String createdServer = platform.getPlayerServer(reporter.uuid());

        CreateTicketRequest request = new CreateTicketRequest(
                reporter.uuid().toString(),
                reporter.username(),
                ticketType,
                subject,
                description.toString(),
                target.uuid().toString(),
                target.username(),
                chatMessages,
                List.of("report"),
                "normal",
                createdServer
        );

        sendMessage(locale.getMessage("messages.submitting", Map.of("type", "report")));

        CompletableFuture<CreateTicketResponse> future = httpClient.createTicket(request);

        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                sendMessage(locale.getMessage("messages.success", Map.of("type", "Report")));
                sendMessage(locale.getMessage("messages.ticket_id", Map.of("ticketId", response.getTicketId())));

                String ticketUrl = panelUrl + "/ticket/" + response.getTicketId();
                sendClickableTicketLink(ticketUrl, response.getTicketId());
                sendMessage(locale.getMessage("messages.evidence_note"));
            } else {
                sendMessage(locale.getMessage("messages.failed_submit",
                        Map.of("type", "report", "error", locale.sanitizeErrorMessage(response.getMessage()))));
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PanelUnavailableException) {
                sendMessage(locale.getMessage("api_errors.panel_restarting"));
            } else {
                sendMessage(locale.getMessage("messages.failed_submit",
                        Map.of("type", "report", "error", locale.sanitizeErrorMessage(throwable.getMessage()))));
            }
            return null;
        });
    }

    private void sendClickableTicketLink(String ticketUrl, String ticketId) {
        String clickableMessage = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"\uD83D\uDCCB \",\"color\":\"gold\"}," +
                "{\"text\":\"View your ticket: \",\"color\":\"gray\"}," +
                "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}",
                ticketUrl, ticketId
        );

        UUID reporterUuid = reporter.uuid();
        platform.runOnMainThread(() -> {
            platform.sendJsonMessage(reporterUuid, clickableMessage);
        });
    }

    private void sendMessage(String message) {
        platform.sendMessage(reporter.uuid(), message);
    }
}
