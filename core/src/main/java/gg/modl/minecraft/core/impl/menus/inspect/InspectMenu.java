package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;

import java.util.UUID;
import java.util.function.Consumer;

public class InspectMenu extends BaseInspectMenu {
    private final InspectContext inspectContext;

    public InspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public InspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, CirrusInventoryType.GENERIC_9X3);
        this.inspectContext = inspectContext;

        title("Inspect: " + targetName);
        activeTab = InspectTab.NONE;
        buildCompactHeader(inspectContext);
    }

    private void buildCompactHeader(InspectContext ctx) {
        if (ctx != null) {
            buildCompactHeader(ctx.punishmentCount(), ctx.noteCount());
        } else {
            buildCompactHeader();
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
    }
}
