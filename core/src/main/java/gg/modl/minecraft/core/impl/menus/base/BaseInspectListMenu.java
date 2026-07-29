package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;

import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseInspectListMenu<T> extends ChromeListMenu<T> {

    protected final Account targetAccount;
    protected final String targetName;
    protected final UUID targetUuid;
    protected final InspectContext inspectContext;

    protected InspectTab activeTab = InspectTab.NONE;

    public BaseInspectListMenu(String title, Platform platform, ModlHttpClient httpClient,
                               UUID viewerUuid, String viewerName, Account targetAccount,
                               Consumer<CirrusPlayerWrapper> backAction) {
        this(title, platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public BaseInspectListMenu(String title, Platform platform, ModlHttpClient httpClient,
                               UUID viewerUuid, String viewerName, Account targetAccount,
                               Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super(title, platform, httpClient, viewerUuid, viewerName, backAction,
                new InspectChrome(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext));
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
        this.inspectContext = inspectContext;
    }

    protected String getPlayerName(Account account) {
        return ReportRenderUtil.getPlayerName(account);
    }

}
