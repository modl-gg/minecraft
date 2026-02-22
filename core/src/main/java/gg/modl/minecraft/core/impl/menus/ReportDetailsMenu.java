package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.actionhandler.ActionHandler;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.text.CirrusChatElement;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
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

/**
 * Add details decision menu (3x9 layout).
 * Asks the player whether to type additional details for the report via chat prompt.
 */
public class ReportDetailsMenu extends SimpleMenu {

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
        // Row 1 layout: * * Y * P * N * *
        // Y (slot 11): Add Details
        set(CirrusItem.of(
                CirrusItemType.WRITABLE_BOOK,
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_add_details")),
                MenuItems.lore(locale.getMessageList("messages.report_add_details_lore"))
        ).slot(11).actionHandler("addDetails"));

        // P (slot 13): Player skull
        List<String> skullLines = locale.getMessageList("messages.report_skull_details", Map.of("player", target.username()));
        CirrusItem detailsHead = MenuItems.playerHead(
                target.username(),
                skullLines.get(0),
                skullLines.subList(1, skullLines.size())
        );
        if (platform.getCache() != null) {
            String texture = platform.getCache().getSkinTexture(target.uuid());
            if (texture != null) detailsHead = detailsHead.texture(texture);
        }
        set(detailsHead.slot(13));

        // N (slot 15): Skip Details
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:wooden_sword"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_skip_details")),
                MenuItems.lore(locale.getMessageList("messages.report_skip_details_lore"))
        ).slot(15).actionHandler("skipDetails"));

        // Row 2 layout: * * * * B * * * *
        // B (slot 22): Back button
        set(MenuItems.backButton().slot(22));
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("addDetails", (ActionHandler) click -> {
            // Close menu and prompt for chat input
            click.clickedMenu().close();

            String prompt = locale.getMessage("messages.report_details_prompt", Map.of("player", target.username()));

            ChatInputManager.requestInput(platform, reporter.uuid(), prompt, input -> {
                // Player typed their details
                reportData.setDetails(input);

                // Open confirm menu on main thread
                platform.runOnMainThread(() -> {
                    ReportConfirmMenu confirmMenu = new ReportConfirmMenu(
                            reporter, target, httpClient, locale, platform, panelUrl,
                            guiConfig, chatMessageCache, reportData
                    );
                    CirrusPlayerWrapper playerWrapper = platform.getPlayerWrapper(reporter.uuid());
                    confirmMenu.display(playerWrapper);
                });
            }, () -> {
                // Cancelled - reopen this menu
                platform.runOnMainThread(() -> {
                    ReportDetailsMenu detailsMenu = new ReportDetailsMenu(
                            reporter, target, httpClient, locale, platform, panelUrl,
                            guiConfig, chatMessageCache, reportData, previousMenu
                    );
                    CirrusPlayerWrapper playerWrapper = platform.getPlayerWrapper(reporter.uuid());
                    detailsMenu.display(playerWrapper);
                });
            });

            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("skipDetails", (ActionHandler) click -> {
            // Proceed without details
            ActionHandlers.openMenu(new ReportConfirmMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache, reportData
            )).handle(click);

            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("back", (ActionHandler) click -> {
            if (previousMenu != null) {
                // Return to previous menu (chat log menu or category menu)
                if (reportData.isChatReport()) {
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
            } else {
                // No previous menu, go to category selection
                ActionHandlers.openMenu(new ReportMenu(
                        reporter, target, httpClient, locale, platform, panelUrl,
                        guiConfig, chatMessageCache
                )).handle(click);
            }

            return CallResult.DENY_GRABBING;
        });
    }
}
