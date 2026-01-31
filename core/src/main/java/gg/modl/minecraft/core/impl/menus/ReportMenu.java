package gg.modl.minecraft.core.impl.menus;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.response.CreateTicketResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import dev.simplix.protocolize.data.inventory.InventoryType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class ReportMenu extends SimpleMenu {
    
    private final AbstractPlayer hero;
    private final AbstractPlayer villain;
    private final String data;
    private final ModlHttpClient httpClient;
    private final LocaleManager locale;
    private final Platform platform;
    private final String panelUrl;
    
    public ReportMenu(AbstractPlayer hero, AbstractPlayer villain, String data, ModlHttpClient httpClient, LocaleManager locale, Platform platform, String panelUrl) {
        super();
        this.hero = hero;
        this.villain = villain;
        this.data = data;
        this.httpClient = httpClient;
        this.locale = locale;
        this.platform = platform;
        this.panelUrl = panelUrl;
        
        title(locale.getMessage("report_gui.title", Map.of("$villain", villain.username())));
        type(InventoryType.GENERIC_9X3); // Fixed size from locale
        buildMenu();
    }
    
    private void buildMenu() {
        // Create report category items from locale
        createCategoryItem("chat_violation", "chat_report");
        createCategoryItem("username", "username_report");
        createCategoryItem("skin", "skin_report");
        createCategoryItem("content", "content_report");
        createCategoryItem("team_griefing", "team_report");
        createCategoryItem("game_rules", "game_report");
        createCategoryItem("cheating", "cheating_report");
        
        // Close button
        LocaleManager.ReportCategory closeButton = locale.getCloseButton();
        List<ChatElement<?>> closeLore = closeButton.getLore().stream()
            .map(ChatElement::ofLegacyText)
            .collect(Collectors.toList());
        set(CirrusItem.of(
                ItemType.valueOf(closeButton.getItemType()),
                ChatElement.ofLegacyText(closeButton.getName()),
                closeLore
            )
            .slot(closeButton.getSlot())
            .actionHandler("close"));
    }
    
    private void createCategoryItem(String categoryName, String actionHandler) {
        LocaleManager.ReportCategory category = locale.getReportCategory(categoryName, villain.username());
        
        List<ChatElement<?>> lore = category.getLore().stream()
            .map(ChatElement::ofLegacyText)
            .collect(Collectors.toList());
        set(CirrusItem.of(
                ItemType.valueOf(category.getItemType()),
                ChatElement.ofLegacyText(category.getName()),
                lore
            )
            .slot(category.getSlot())
            .actionHandler(actionHandler));
    }
    
    @Override
    protected void registerActionHandlers() {
        registerActionHandler("chat_report", click -> {
            click.clickedMenu().close();
            LocaleManager.ReportCategory category = locale.getReportCategory("chat_violation", villain.username());
            submitReport(category.getReportType(), category.getSubject());
        });
        
        registerActionHandler("username_report", click -> {
            click.clickedMenu().close();
            LocaleManager.ReportCategory category = locale.getReportCategory("username", villain.username());
            submitReport(category.getReportType(), category.getSubject());
        });
        
        registerActionHandler("skin_report", click -> {
            click.clickedMenu().close();
            LocaleManager.ReportCategory category = locale.getReportCategory("skin", villain.username());
            submitReport(category.getReportType(), category.getSubject());
        });
        
        registerActionHandler("content_report", click -> {
            click.clickedMenu().close();
            LocaleManager.ReportCategory category = locale.getReportCategory("content", villain.username());
            submitReport(category.getReportType(), category.getSubject());
        });
        
        registerActionHandler("team_report", click -> {
            click.clickedMenu().close();
            LocaleManager.ReportCategory category = locale.getReportCategory("team_griefing", villain.username());
            submitReport(category.getReportType(), category.getSubject());
        });
        
        registerActionHandler("game_report", click -> {
            click.clickedMenu().close();
            LocaleManager.ReportCategory category = locale.getReportCategory("game_rules", villain.username());
            submitReport(category.getReportType(), category.getSubject());
        });
        
        registerActionHandler("cheating_report", click -> {
            click.clickedMenu().close();
            LocaleManager.ReportCategory category = locale.getReportCategory("cheating", villain.username());
            submitReport(category.getReportType(), category.getSubject());
        });
        
        registerActionHandler("close", click -> {
            click.clickedMenu().close();
        });
    }
    
    private void submitReport(String type, String subject) {
        
        CreateTicketRequest request = new CreateTicketRequest(
            hero.uuid().toString(),                                         // creatorUuid
            hero.username(),                                         // creatorName
            type,                                               // type
            subject + ": " + villain.username(),                     // subject
            data != null ? data : locale.getMessage("messages.no_details", Map.of()), // description
            villain.uuid().toString(),                                     // reportedPlayerUuid
            villain.username(),                                       // reportedPlayerName
            null,                                              // TODO: chatMessages
            List.of(), // tags
            "normal"                           // priority
        );
        
        sendMessage(locale.getMessage("messages.submitting", Map.of("type", "report")));
        
        CompletableFuture<CreateTicketResponse> future = httpClient.createTicket(request);
        
        future.thenAccept(response -> {
            if (response.isSuccess() && response.getTicketId() != null) {
                sendMessage(locale.getMessage("messages.success", Map.of("type", "Report")));
                sendMessage(locale.getMessage("messages.ticket_id", Map.of("ticketId", response.getTicketId())));
                
                String ticketUrl = panelUrl + "/tickets/" + response.getTicketId();
                sendMessage(locale.getMessage("messages.view_ticket", Map.of("url", ticketUrl)));
                sendMessage(locale.getMessage("messages.evidence_note"));
            } else {
                sendMessage(locale.getMessage("messages.failed_submit", Map.of("type", "report", "error", locale.sanitizeErrorMessage(response.getMessage()))));
            }
        }).exceptionally(throwable -> {
            sendMessage(locale.getMessage("messages.failed_submit", Map.of("type", "report", "error", locale.sanitizeErrorMessage(throwable.getMessage()))));
            return null;
        });
    }


    private void sendMessage(String message) {
        platform.sendMessage(hero.uuid(), message);
    }
}