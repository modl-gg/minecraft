package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.actionhandler.ActionHandler;
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

import java.util.List;
import java.util.Map;

public class ReportChatLogMenu extends SimpleMenu {
    private static final int SLOT_ATTACH_CHAT = 11;
    private static final int SLOT_PLAYER_HEAD = 13;
    private static final int SLOT_SKIP_CHAT = 15;
    private static final int SLOT_BACK = 22;

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
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:orange_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_attach_chat")),
                MenuItems.lore(locale.getMessageList("messages.report_attach_chat_lore"))
        ).slot(SLOT_ATTACH_CHAT).actionHandler("attachChat"));

        set(buildTargetHead("messages.report_skull_chat_log").slot(SLOT_PLAYER_HEAD));

        set(CirrusItem.of(
                CirrusItemType.of("minecraft:light_blue_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_skip_chat")),
                MenuItems.lore(locale.getMessageList("messages.report_skip_chat_lore"))
        ).slot(SLOT_SKIP_CHAT).actionHandler("skipChat"));

        set(MenuItems.backButton().slot(SLOT_BACK));
    }

    private CirrusItem buildTargetHead(String localeKey) {
        List<String> skullLines = locale.getMessageList(localeKey, Map.of("player", target.username()));
        CirrusItem head = MenuItems.playerHead(
                target.username(),
                skullLines.get(0),
                skullLines.subList(1, skullLines.size())
        );
        if (platform.getCache() != null) {
            String texture = platform.getCache().getSkinTexture(target.uuid());
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
        registerActionHandler("attachChat", (ActionHandler) click -> {
            String chatLog = chatMessageCache.getChatLogForReport(
                    target.uuid().toString(),
                    reporter.uuid().toString()
            );
            if (chatLog.isEmpty()) {
                platform.sendMessage(reporter.uuid(),
                        locale.getMessage("messages.no_chat_logs_available", Map.of("player", target.username())));
            } else {
                reportData.setChatLog(chatLog);
            }
            // Proceed to details even if no chat logs were found
            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("skipChat", (ActionHandler) click -> {
            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("back", (ActionHandler) click -> {
            ActionHandlers.openMenu(new ReportMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache
            )).handle(click);
            return CallResult.DENY_GRABBING;
        });
    }
}
