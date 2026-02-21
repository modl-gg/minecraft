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
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;

import java.util.List;
import java.util.Map;

/**
 * Chat log decision menu (3x9 layout).
 * Asks the player whether to attach recent chat logs to the report.
 */
public class ReportChatLogMenu extends SimpleMenu {

    private final AbstractPlayer reporter;
    private final AbstractPlayer target;
    private final ModlHttpClient httpClient;
    private final LocaleManager locale;
    private final Platform platform;
    private final String panelUrl;
    private final ReportGuiConfig guiConfig;
    private final ChatMessageCache chatMessageCache;
    private final ReportData reportData;

    public ReportChatLogMenu(AbstractPlayer reporter, AbstractPlayer target, ModlHttpClient httpClient,
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
        // Row 1 layout: * * Y * P * N * *
        // Y (slot 11): Attach Chat Log
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:orange_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_attach_chat")),
                MenuItems.lore(locale.getMessageList("messages.report_attach_chat_lore"))
        ).slot(11).actionHandler("attachChat"));

        // P (slot 13): Player skull
        List<String> skullLines = locale.getMessageList("messages.report_skull_chat_log", Map.of("player", target.username()));
        set(MenuItems.playerHead(
                target.username(),
                skullLines.get(0),
                skullLines.subList(1, skullLines.size())
        ).slot(13));

        // N (slot 15): Skip Chat Log
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:light_blue_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_skip_chat")),
                MenuItems.lore(locale.getMessageList("messages.report_skip_chat_lore"))
        ).slot(15).actionHandler("skipChat"));

        // Row 2 layout: * * * * B * * * *
        // B (slot 22): Back button
        set(MenuItems.backButton().slot(22));
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("attachChat", (ActionHandler) click -> {
            // Fetch chat log and store in report data
            String chatLog = chatMessageCache.getChatLogForReport(
                    target.uuid().toString(),
                    reporter.uuid().toString()
            );

            if (chatLog.isEmpty()) {
                platform.sendMessage(reporter.uuid(),
                        locale.getMessage("messages.no_chat_logs_available", Map.of("player", target.username())));
                // Still proceed to details menu, just without chat log
            } else {
                reportData.setChatLog(chatLog);
            }

            // Open details menu
            ActionHandlers.openMenu(new ReportDetailsMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache, reportData, this
            )).handle(click);

            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("skipChat", (ActionHandler) click -> {
            // Proceed without chat log
            ActionHandlers.openMenu(new ReportDetailsMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache, reportData, this
            )).handle(click);

            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("back", (ActionHandler) click -> {
            // Return to category selection
            ActionHandlers.openMenu(new ReportGuiMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache
            )).handle(click);

            return CallResult.DENY_GRABBING;
        });
    }
}
