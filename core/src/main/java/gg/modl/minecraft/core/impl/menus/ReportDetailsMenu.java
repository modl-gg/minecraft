package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.actionhandler.ActionHandler;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;

import java.util.List;
import java.util.Map;

public class ReportDetailsMenu extends SimpleMenu {

    private static final int SLOT_ADD_DETAILS = 11;
    private static final int SLOT_PLAYER_HEAD = 13;
    private static final int SLOT_SKIP_DETAILS = 15;
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
    private final SimpleMenu previousMenu;

    public ReportDetailsMenu(AbstractPlayer reporter, AbstractPlayer target, ModlHttpClient httpClient,
                              LocaleManager locale, Platform platform, String panelUrl,
                              ReportGuiConfig guiConfig, ChatMessageCache chatMessageCache,
                              ReportData reportData, SimpleMenu previousMenu) {
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
        this.previousMenu = previousMenu;
        title(locale.getMessage("messages.report_gui_title", Map.of("player", target.username())));
        type(CirrusInventoryType.GENERIC_9X3);
        buildMenu();
    }

    private void buildMenu() {
        set(CirrusItem.of(
                CirrusItemType.WRITABLE_BOOK,
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_add_details")),
                MenuItems.lore(locale.getMessageList("messages.report_add_details_lore"))
        ).slot(SLOT_ADD_DETAILS).actionHandler("addDetails"));

        set(buildTargetHead("messages.report_skull_details").slot(SLOT_PLAYER_HEAD));

        set(CirrusItem.of(
                CirrusItemType.of("minecraft:wooden_sword"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_skip_details")),
                MenuItems.lore(locale.getMessageList("messages.report_skip_details_lore"))
        ).slot(SLOT_SKIP_DETAILS).actionHandler("skipDetails"));

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

    private void displayMenu(SimpleMenu menu) {
        CirrusPlayerWrapper playerWrapper = platform.getPlayerWrapper(reporter.uuid());
        menu.display(playerWrapper);
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("addDetails", (ActionHandler) click -> {
            click.clickedMenu().close();
            String prompt = locale.getMessage("messages.report_details_prompt", Map.of("player", target.username()));
            ChatInputManager.requestInput(platform, reporter.uuid(), prompt, input -> {
                reportData.setDetails(input);
                displayMenu(new ReportConfirmMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache, reportData
                ));
            }, () -> {
                displayMenu(new ReportDetailsMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache, reportData, previousMenu
                ));
            });
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("skipDetails", (ActionHandler) click -> {
            ActionHandlers.openMenu(new ReportConfirmMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache, reportData
            )).handle(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("back", (ActionHandler) click -> {
            // Chat reports go back to chat log menu; all others go to category selection
            if (previousMenu != null && reportData.isChatReport()) {
                ActionHandlers.openMenu(new ReportChatLogMenu(
                        reporter, target, httpClient, locale, platform, panelUrl,
                        guiConfig, chatMessageCache, reportData
                )).handle(click);
            } else {
                ActionHandlers.openMenu(new ReportMenu(
                        reporter, target, httpClient, locale, platform, panelUrl,
                        guiConfig, chatMessageCache
                )).handle(click);
            }
            return CallResult.DENY_GRABBING;
        });
    }
}
