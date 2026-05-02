package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.ReplayService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public class ReportConfirmMenu extends SimpleMenu {
    private final AbstractPlayer reporter, target;
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

        title(locale.getMessage("messages.report_gui_title", mapOf("player", target.getUsername())));
        type(CirrusInventoryType.GENERIC_9X3);
        buildMenu();
    }

    private void buildMenu() {
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(MenuItems.COLOR_GRAY + "Reason: " + MenuItems.COLOR_WHITE + reportData.getReason());
        if (reportData.getDetails() != null) confirmLore.add(MenuItems.COLOR_GRAY + "Details: " + MenuItems.COLOR_WHITE + reportData.getDetails());
        if (reportData.getChatLog() != null) confirmLore.add(MenuItems.COLOR_GRAY + "Chat Log: " + MenuItems.COLOR_WHITE + "Attached");
        if (reportData.isAttachReplay()) confirmLore.add(MenuItems.COLOR_GRAY + "Replay: " + MenuItems.COLOR_GREEN + "Will be captured");
        confirmLore.add("");
        confirmLore.addAll(locale.getMessageList("messages.report_confirm_lore"));

        set(CirrusItem.of(
                CirrusItemType.of("minecraft:lime_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_confirm")),
                MenuItems.lore(confirmLore)
        ).slot(11).actionHandler("confirm"));

        CirrusItem confirmHead = ReportRenderUtil.buildTargetHead(locale, platform, target, "messages.report_skull_confirm");
        set(confirmHead.slot(13));

        set(CirrusItem.of(
                CirrusItemType.of("minecraft:red_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_cancel")),
                MenuItems.lore(locale.getMessageList("messages.report_cancel_lore"))
        ).slot(15).actionHandler("cancel"));

        if (reportData.isReplayCapture()) {
            boolean replayOn = reportData.isAttachReplay();
            CirrusItem replayItem = CirrusItem.of(
                    CirrusItemType.of("minecraft:ender_eye"),
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Attach Replay"),
                    MenuItems.lore(listOf(
                            MenuItems.COLOR_GRAY + "Click to toggle replay attachment",
                            "",
                            replayOn
                                    ? MenuItems.COLOR_GREEN + "Replay will be captured"
                                    : MenuItems.COLOR_RED + "Replay will NOT be captured"
                    ))
            ).slot(4).actionHandler("toggleReplay");
            if (replayOn) replayItem.glow();
            set(replayItem);
        }

        set(MenuItems.backButton().slot(22));
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("confirm", click -> {
            click.clickedMenu().close();
            submitReport();
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("cancel", click -> {
            click.clickedMenu().close();
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("toggleReplay", click -> {
            reportData.setAttachReplay(!reportData.isAttachReplay());
            buildMenu();
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("back", click -> {
            ActionHandlers.openMenu(new ReportDetailsMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache, reportData, null
            )).handle(click);

            return CallResult.DENY_GRABBING;
        });
    }

    private void submitReport() {
        StringBuilder description = new StringBuilder();
        description.append("Reason: ").append(reportData.getReason());

        if (reportData.getDetails() != null) description.append("\nDetails: ").append(reportData.getDetails());

        if (reportData.getChatLog() != null) description.append("\n\n**Chat Log:**\n```\n").append(reportData.getChatLog()).append("\n```");

        String ticketType = reportData.isChatReport() ? "chat" : "player";
        String subject = (reportData.isChatReport() ? "Chat Report" : "Player Report") + ": " + target.getUsername();

        List<String> chatMessages = null;
        if (reportData.getChatLog() != null) chatMessages = listOf(reportData.getChatLog().split("\n"));

        String createdServer = platform.getPlayerServer(reporter.getUuid());

        sendMessage(locale.getMessage("messages.submitting", mapOf("type", "report")));

        CompletableFuture<String> replayFuture;
        ReplayService replayService = platform.getReplayService();
        if (reportData.isAttachReplay() && replayService != null && replayService.isReplayAvailable(target.getUuid())) {
            replayFuture = replayService.captureReplay(target.getUuid(), target.getUsername());
        } else {
            replayFuture = CompletableFuture.completedFuture(null);
        }

        List<String> finalChatMessages = chatMessages;
        replayFuture.whenComplete((replayUrl, replayEx) -> {
            if (replayEx != null) replayUrl = null;

            CreateTicketRequest request = new CreateTicketRequest(
                    reporter.getUuid().toString(),
                    ticketType,
                    reporter.getUsername(),
                    subject,
                    description.toString(),
                    target.getUuid().toString(),
                    target.getUsername(),
                    "normal",
                    createdServer,
                    finalChatMessages,
                    listOf("report"),
                    replayUrl
            );

            CompletableFuture<CreateTicketResponse> future = httpClient.createTicket(request);

            future.thenAccept(response -> {
                if (response.isSuccess() && response.getTicketId() != null) {
                    sendMessage(locale.getMessage("messages.success", mapOf("type", "Report")));
                    sendMessage(locale.getMessage("messages.ticket_id", mapOf("ticketId", response.getTicketId())));

                    String ticketUrl = panelUrl + "/panel/tickets/" + response.getTicketId();
                    sendClickableTicketLink(ticketUrl, response.getTicketId());
                    sendMessage(locale.getMessage("messages.evidence_note"));
                } else {
                    sendMessage(locale.getMessage("messages.failed_submit",
                            mapOf("type", "report", "error", locale.sanitizeErrorMessage(response.getMessage()))));
                }
            }).exceptionally(throwable -> {
                if (throwable.getCause() instanceof PanelUnavailableException) {
                    sendMessage(locale.getMessage("api_errors.panel_restarting"));
                } else {
                    sendMessage(locale.getMessage("messages.failed_submit",
                            mapOf("type", "report", "error", locale.sanitizeErrorMessage(throwable.getMessage()))));
                }
                return null;
            });
        });
    }

    private void sendClickableTicketLink(String ticketUrl, String ticketId) {
        String clickableMessage = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"\",\"color\":\"gold\"}," +
                "{\"text\":\"View your ticket: \",\"color\":\"gray\"}," +
                "{\"text\":\"[Click to view]\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to view ticket %s\"}}]}",
                ticketUrl, ticketId
        );

        UUID reporterUuid = reporter.getUuid();
        platform.runOnMainThread(() -> platform.sendJsonMessage(reporterUuid, clickableMessage));
    }

    private void sendMessage(String message) {
        platform.sendMessage(reporter.getUuid(), message);
    }
}
