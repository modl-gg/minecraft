package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.PlayerHeadItemBuilder;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.TargetPlayerAction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class InspectChrome implements MenuChrome {

    private final Platform platform;
    private final ModlHttpClient httpClient;
    private final UUID viewerUuid;
    private final String viewerName;
    private final Account targetAccount;
    private final String targetName;
    private final UUID targetUuid;
    private final Consumer<CirrusPlayerWrapper> navBackAction;
    private final InspectContext inspectContext;

    public InspectChrome(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         Account targetAccount, Consumer<CirrusPlayerWrapper> navBackAction, InspectContext inspectContext) {
        this.platform = platform;
        this.httpClient = httpClient;
        this.viewerUuid = viewerUuid;
        this.viewerName = viewerName;
        this.targetAccount = targetAccount;
        this.targetName = ReportRenderUtil.getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
        this.navBackAction = navBackAction;
        this.inspectContext = inspectContext;
    }

    @Override
    public Map<Integer, CirrusItem> headerItems(boolean compact) {
        Map<Integer, CirrusItem> items = new LinkedHashMap<>();
        int headSlot = compact ? MenuSlots.COMPACT_INSPECT_HEAD : MenuSlots.INSPECT_PLAYER_HEAD;
        items.put(headSlot, new PlayerHeadItemBuilder().create(platform, targetAccount, targetName, targetUuid)
                .actionHandler("targetPlayer"));
        items.putAll(tabItems(compact));
        return items;
    }

    private Map<Integer, CirrusItem> tabItems(boolean compact) {
        if (compact) {
            return inspectContext != null
                    ? InspectTabItems.createCompactItems(targetAccount, targetName, inspectContext.punishmentCount(), inspectContext.noteCount())
                    : InspectTabItems.createCompactItems(targetAccount, targetName);
        }
        return inspectContext != null
                ? InspectTabItems.createItems(targetAccount, targetName, inspectContext.punishmentCount(), inspectContext.noteCount())
                : InspectTabItems.createItems(targetAccount, targetName);
    }

    @Override
    public void registerNavigation(BiConsumer<String, Consumer<Click>> registrar) {
        registrar.accept("targetPlayer", click ->
                TargetPlayerAction.handle(click, platform, viewerUuid, targetUuid, targetName));
        InspectNavigationHandlers.registerAll(registrar, platform, httpClient, viewerUuid, viewerName,
                targetAccount, navBackAction, inspectContext);
    }
}
