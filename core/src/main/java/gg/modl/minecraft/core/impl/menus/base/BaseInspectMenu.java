package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.PlayerHeadItemBuilder;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.TargetPlayerAction;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseInspectMenu extends BaseMenu {

    protected final Account targetAccount;
    protected final String targetName;
    protected final UUID targetUuid;

    protected InspectTab activeTab = InspectTab.NONE;

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, backAction);
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
    }

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType) {
        super(platform, httpClient, viewerUuid, viewerName, backAction, inventoryType);
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
    }

    protected void buildHeader() {
        fillBorders();
        set(PlayerHeadItemBuilder.create(platform, targetAccount, targetName, targetUuid)
                .actionHandler("targetPlayer").slot(MenuSlots.INSPECT_PLAYER_HEAD));
        for (Map.Entry<Integer, CirrusItem> entry : InspectTabItems.createItems(targetAccount, targetName).entrySet()) {
            set(entry.getValue().slot(entry.getKey()));
        }
        addBackButton();
    }

    protected void buildCompactHeader() {
        set(PlayerHeadItemBuilder.create(platform, targetAccount, targetName, targetUuid)
                .actionHandler("targetPlayer").slot(MenuSlots.COMPACT_INSPECT_HEAD));
        for (Map.Entry<Integer, CirrusItem> entry : InspectTabItems.createCompactItems(targetAccount, targetName).entrySet()) {
            set(entry.getValue().slot(entry.getKey()));
        }
        addCompactBackButton();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();
        registerActionHandler("targetPlayer", (dev.simplix.cirrus.model.Click click) ->
                TargetPlayerAction.handle(click, platform, viewerUuid, targetUuid, targetName));
    }

    protected String getPlayerName(Account account) {
        return ReportRenderUtil.getPlayerName(account);
    }

}
