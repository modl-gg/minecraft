package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.PlayerHeadItemBuilder;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.TargetPlayerAction;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import dev.simplix.cirrus.model.Click;

public abstract class BaseInspectListMenu<T> extends BaseListMenu<T> {

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
        super(title, platform, httpClient, viewerUuid, viewerName, backAction);
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
        this.inspectContext = inspectContext;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);
        items.put(MenuSlots.INSPECT_PLAYER_HEAD,
                PlayerHeadItemBuilder.create(platform, targetAccount, targetName, targetUuid)
                        .actionHandler("targetPlayer"));
        if (inspectContext != null) {
            items.putAll(InspectTabItems.createItems(targetAccount, targetName,
                    inspectContext.punishmentCount(), inspectContext.noteCount()));
        } else {
            items.putAll(InspectTabItems.createItems(targetAccount, targetName));
        }
        return items;
    }

    protected String getPlayerName(Account account) {
        return ReportRenderUtil.getPlayerName(account);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();
        registerActionHandler("targetPlayer", (Click click) ->
                TargetPlayerAction.handle(click, platform, viewerUuid, targetUuid, targetName));
    }

}
