package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.actionhandler.ActionHandler;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.text.CirrusChatElement;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
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
 * Base category selection menu for player reports (6x9 layout).
 * Players choose a report category, then proceed through the multi-step flow.
 */
public class ReportGuiMenu extends SimpleMenu {

    private final AbstractPlayer reporter;
    private final AbstractPlayer target;
    private final ModlHttpClient httpClient;
    private final LocaleManager locale;
    private final Platform platform;
    private final String panelUrl;
    private final ReportGuiConfig guiConfig;
    private final ChatMessageCache chatMessageCache;

    // Slot mapping: config slots 1-7 -> GUI row 3 (slots 19-25), slots 8-14 -> GUI row 4 (slots 28-34)
    private static final int[] GUI_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public ReportGuiMenu(AbstractPlayer reporter, AbstractPlayer target, ModlHttpClient httpClient,
                          LocaleManager locale, Platform platform, String panelUrl,
                          ReportGuiConfig guiConfig, ChatMessageCache chatMessageCache) {
        super();
        this.reporter = reporter;
        this.target = target;
        this.httpClient = httpClient;
        this.locale = locale;
        this.platform = platform;
        this.panelUrl = panelUrl;
        this.guiConfig = guiConfig;
        this.chatMessageCache = chatMessageCache;

        title(locale.getMessage("messages.report_gui_title", Map.of("player", target.username())));
        type(CirrusInventoryType.GENERIC_9X6);
        buildMenu();
    }

    private void buildMenu() {
        // Row 0, slot 4: Player head of target
        set(MenuItems.playerHead(
                target.username(),
                locale.getMessage("messages.report_gui_title", Map.of("player", target.username())),
                List.of()
        ).slot(4));

        // Rows 2-3: Category items from config
        for (int configSlot = 1; configSlot <= 14; configSlot++) {
            ReportGuiConfig.ReportSlotConfig slotConfig = guiConfig.getSlot(configSlot);
            if (slotConfig == null || !slotConfig.isEnabled()) {
                continue;
            }

            int guiSlot = GUI_SLOTS[configSlot - 1];
            set(createCategoryItem(slotConfig).slot(guiSlot));
        }

        // Row 5, slot 49: Info item
        ReportGuiConfig.InfoConfig infoConfig = guiConfig.getInfoConfig();
        if (infoConfig != null) {
            set(CirrusItem.of(
                    CirrusItemType.of(infoConfig.getItem()),
                    CirrusChatElement.ofLegacyText(MenuItems.translateColorCodes(infoConfig.getTitle())),
                    MenuItems.lore(infoConfig.getDescription())
            ).slot(48));
        }

        // Row 5, slot 49: Close button
        set(CirrusItem.of(
                CirrusItemType.BARRIER,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "Close"),
                MenuItems.lore(MenuItems.COLOR_GRAY + "Close this menu")
        ).slot(49).actionHandler("close"));
    }

    private CirrusItem createCategoryItem(ReportGuiConfig.ReportSlotConfig slotConfig) {
        return CirrusItem.of(
                CirrusItemType.of(slotConfig.getItem()),
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + slotConfig.getTitle()),
                MenuItems.lore(slotConfig.getDescription())
        ).actionHandler("category_" + slotConfig.getSlotNumber());
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("close", click -> {
            click.clickedMenu().close();
        });

        // Register handler for each enabled category slot
        for (int configSlot = 1; configSlot <= 14; configSlot++) {
            ReportGuiConfig.ReportSlotConfig slotConfig = guiConfig.getSlot(configSlot);
            if (slotConfig == null || !slotConfig.isEnabled()) {
                continue;
            }

            final ReportGuiConfig.ReportSlotConfig finalSlot = slotConfig;
            registerActionHandler("category_" + configSlot, (ActionHandler) click -> {
                ReportData reportData = new ReportData(finalSlot.getTitle(), finalSlot.isChatReport());

                if (finalSlot.isChatReport()) {
                    // Open chat log menu
                    ActionHandlers.openMenu(new ReportChatLogMenu(
                            reporter, target, httpClient, locale, platform, panelUrl,
                            guiConfig, chatMessageCache, reportData
                    )).handle(click);
                } else {
                    // Skip chat log, go to details menu
                    ActionHandlers.openMenu(new ReportDetailsMenu(
                            reporter, target, httpClient, locale, platform, panelUrl,
                            guiConfig, chatMessageCache, reportData, null
                    )).handle(click);
                }

                return CallResult.DENY_GRABBING;
            });
        }
    }
}
