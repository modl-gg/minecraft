package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;

import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseInspectMenu extends ChromeMenu {

    protected final Account targetAccount;
    protected final String targetName;
    protected final UUID targetUuid;

    protected InspectTab activeTab = InspectTab.NONE;

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, backAction,
                CirrusInventoryType.GENERIC_9X6, null);
    }

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction,
                           Consumer<CirrusPlayerWrapper> navBackAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, navBackAction,
                CirrusInventoryType.GENERIC_9X6, null);
    }

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, backAction, inventoryType, null);
    }

    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType,
                           InspectContext inspectContext) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, backAction, inventoryType, inspectContext);
    }

    private BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                            Account targetAccount, Consumer<CirrusPlayerWrapper> backAction,
                            Consumer<CirrusPlayerWrapper> navBackAction, CirrusInventoryType inventoryType,
                            InspectContext inspectContext) {
        super(platform, httpClient, viewerUuid, viewerName, backAction,
                new InspectChrome(platform, httpClient, viewerUuid, viewerName, targetAccount, navBackAction, inspectContext),
                inventoryType);
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
    }

    protected String getPlayerName(Account account) {
        return ReportRenderUtil.getPlayerName(account);
    }

}
