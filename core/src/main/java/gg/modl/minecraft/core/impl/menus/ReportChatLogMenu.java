package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.ReplayService;

import java.util.List;
import java.util.Map;

/**
 * Attachments step in the report flow.
 * Shows available attachment options based on report type and server capabilities:
 * - Chat log attachment (for chat reports)
 * - Replay capture (for gameplay reports when replay recording is available)
 * - Both (for chat reports on servers with replay recording)
 */
public class ReportChatLogMenu extends SimpleMenu {

    private final AbstractPlayer reporter, target;
    private final ModlHttpClient httpClient;
    private final LocaleManager locale;
    private final Platform platform;
    private final String panelUrl;
    private final ReportGuiConfig guiConfig;
    private final ChatMessageCache chatMessageCache;
    private final ReportData reportData;

    private final boolean showChat;
    private final boolean showReplay;
    private boolean chatToggled = false;
    private boolean replayToggled = false;

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

        this.showChat = reportData.isChatReport();
        this.showReplay = reportData.isReplayCapture();

        // Restore toggle state from reportData (for back navigation)
        this.chatToggled = reportData.getChatLog() != null;
        this.replayToggled = reportData.isAttachReplay();

        title(locale.getMessage("messages.report_gui_title", Map.of("player", target.getUsername())));
        type(CirrusInventoryType.GENERIC_9X3);
        buildMenu();
    }

    private void buildMenu() {
        if (showChat && showReplay) {
            buildCombinedMenu();
        } else if (showChat) {
            buildChatOnlyMenu();
        } else {
            buildReplayOnlyMenu();
        }
    }

    /**
     * Both chat and replay options — toggles with a continue button.
     * Layout: head(4), chat(10), replay(16), continue(22), back(26)
     */
    private void buildCombinedMenu() {
        set(buildTargetHead("messages.report_skull_chat_log").slot(4));

        // Chat toggle
        CirrusItem chatItem = CirrusItem.of(
                CirrusItemType.of(chatToggled ? "minecraft:lime_terracotta" : "minecraft:orange_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_attach_chat")),
                MenuItems.lore(List.of(
                        chatToggled
                                ? MenuItems.COLOR_GREEN + "Chat log will be attached"
                                : MenuItems.COLOR_GRAY + "Click to attach chat log"
                ))
        ).slot(10).actionHandler("toggleChat");
        if (chatToggled) chatItem.glow();
        set(chatItem);

        // Replay toggle
        CirrusItem replayItem = CirrusItem.of(
                CirrusItemType.of("minecraft:ender_eye"),
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Capture Replay"),
                MenuItems.lore(List.of(
                        MenuItems.COLOR_GRAY + "Captures recent gameplay recording",
                        "",
                        replayToggled
                                ? MenuItems.COLOR_GREEN + "Replay will be captured"
                                : MenuItems.COLOR_RED + "Replay will NOT be captured"
                ))
        ).slot(16).actionHandler("toggleReplay");
        if (replayToggled) replayItem.glow();
        set(replayItem);

        // Continue button
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:lime_terracotta"),
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Continue"),
                MenuItems.lore(List.of(MenuItems.COLOR_GRAY + "Proceed to add details"))
        ).slot(22).actionHandler("continue"));

        // Back button
        set(MenuItems.backButton().slot(26));
    }

    /**
     * Chat only (no replay available) — original behavior.
     * Layout: chat(11), head(13), skip(15), back(22)
     */
    private void buildChatOnlyMenu() {
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:orange_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_attach_chat")),
                MenuItems.lore(locale.getMessageList("messages.report_attach_chat_lore"))
        ).slot(11).actionHandler("attachChat"));

        set(buildTargetHead("messages.report_skull_chat_log").slot(13));

        set(CirrusItem.of(
                CirrusItemType.of("minecraft:light_blue_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_skip_chat")),
                MenuItems.lore(locale.getMessageList("messages.report_skip_chat_lore"))
        ).slot(15).actionHandler("skipChat"));

        set(MenuItems.backButton().slot(22));
    }

    /**
     * Replay only (non-chat report with replay available).
     * Layout: replay(11), head(13), skip(15), back(22)
     */
    private void buildReplayOnlyMenu() {
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:ender_eye"),
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Capture Replay"),
                MenuItems.lore(List.of(
                        MenuItems.COLOR_GRAY + "Captures recent gameplay recording",
                        MenuItems.COLOR_GRAY + "and attaches it to the report",
                        "",
                        MenuItems.COLOR_YELLOW + "Click to attach replay"
                ))
        ).slot(11).actionHandler("captureReplay"));

        set(buildTargetHead("messages.report_skull_chat_log").slot(13));

        set(CirrusItem.of(
                CirrusItemType.of("minecraft:light_blue_terracotta"),
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Skip"),
                MenuItems.lore(List.of(MenuItems.COLOR_GRAY + "Continue without replay"))
        ).slot(15).actionHandler("skipReplay"));

        set(MenuItems.backButton().slot(22));
    }

    private CirrusItem buildTargetHead(String localeKey) {
        List<String> skullLines = locale.getMessageList(localeKey, Map.of("player", target.getUsername()));
        CirrusItem head = MenuItems.playerHead(
                skullLines.get(0),
                skullLines.subList(1, skullLines.size())
        );
        if (platform.getCache() != null) {
            String texture = platform.getCache().getSkinTexture(target.getUuid());
            if (texture != null) head = head.texture(texture);
        }
        return head;
    }

    private void openDetailsMenu(Click click) {
        ActionHandlers.openMenu(new ReportDetailsMenu(
                reporter, target, httpClient, locale, platform, panelUrl,
                guiConfig, chatMessageCache, reportData, this
        )).handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        // === Chat-only mode handlers ===

        registerActionHandler("attachChat", click -> {
            String chatLog = chatMessageCache.getChatLogForReport(
                    target.getUuid().toString(),
                    reporter.getUuid().toString()
            );

            if (chatLog.isEmpty()) {
                platform.sendMessage(reporter.getUuid(),
                        locale.getMessage("messages.no_chat_logs_available", Map.of("player", target.getUsername())));
            } else {
                reportData.setChatLog(chatLog);
            }

            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("skipChat", click -> {
            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        // === Replay-only mode handlers ===

        registerActionHandler("captureReplay", click -> {
            reportData.setAttachReplay(true);
            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("skipReplay", click -> {
            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        // === Combined mode handlers (toggles + continue) ===

        registerActionHandler("toggleChat", click -> {
            chatToggled = !chatToggled;
            if (chatToggled) {
                String chatLog = chatMessageCache.getChatLogForReport(
                        target.getUuid().toString(),
                        reporter.getUuid().toString()
                );
                if (chatLog.isEmpty()) {
                    platform.sendMessage(reporter.getUuid(),
                            locale.getMessage("messages.no_chat_logs_available", Map.of("player", target.getUsername())));
                    chatToggled = false;
                } else {
                    reportData.setChatLog(chatLog);
                }
            } else {
                reportData.setChatLog(null);
            }
            buildMenu();
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("toggleReplay", click -> {
            replayToggled = !replayToggled;
            reportData.setAttachReplay(replayToggled);
            buildMenu();
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("continue", click -> {
            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        // === Common ===

        registerActionHandler("back", click -> {
            ActionHandlers.openMenu(new ReportMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache
            )).handle(click);
            return CallResult.DENY_GRABBING;
        });
    }
}
