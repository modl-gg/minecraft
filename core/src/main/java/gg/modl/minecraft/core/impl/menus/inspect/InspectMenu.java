package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Primary Inspect Menu - shows player information and navigation to sub-menus.
 * Accessible via /inspect <player>
 */
public class InspectMenu extends BaseInspectMenu {

    /**
     * Create a new inspect menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to perform when back button is clicked (null if opened directly)
     */
    public InspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, CirrusInventoryType.GENERIC_9X3);

        title("Inspect: " + targetName);
        activeTab = InspectTab.NONE;
        buildCompactHeader();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Create a back action that returns to this InspectMenu (not the original parent).
        // Secondary menus should always navigate back to InspectMenu when pressing back,
        // regardless of how InspectMenu itself was opened (e.g., from Staff Menu).
        Consumer<CirrusPlayerWrapper> inspectBackAction = player ->
                new InspectMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                        .display(player);

        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, inspectBackAction)));

        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, inspectBackAction)));

        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, inspectBackAction)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, inspectBackAction)));

        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, inspectBackAction)));
    }
}
