package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.TargetPlayerAction;

import java.util.UUID;
import java.util.function.Consumer;
import dev.simplix.cirrus.model.Click;

public abstract class BaseInspectMenu extends ChromeMenu {

    protected final Account targetAccount;
    protected final String targetName;
    protected final UUID targetUuid;

    protected InspectTab activeTab = InspectTab.NONE;

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, backAction,
                new InspectChrome(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null));
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
    }

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inventoryType, null);
    }

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType,
                           InspectContext inspectContext) {
        super(platform, httpClient, viewerUuid, viewerName, backAction,
                new InspectChrome(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext),
                inventoryType);
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();
        registerActionHandler("targetPlayer", (Click click) ->
                TargetPlayerAction.handle(click, platform, viewerUuid, targetUuid, targetName));
    }

    protected String getPlayerName(Account account) {
        return ReportRenderUtil.getPlayerName(account);
    }

}
