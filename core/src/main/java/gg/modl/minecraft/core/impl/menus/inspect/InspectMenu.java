package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.model.Click;
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
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);

        title("Inspect: " + targetName);
        activeTab = InspectTab.NONE;
        buildHeader();
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Open Notes menu
        registerActionHandler("openNotes", this::openNotes);

        // Open Alts menu
        registerActionHandler("openAlts", this::openAlts);

        // Open History menu
        registerActionHandler("openHistory", this::openHistory);

        // Open Reports menu
        registerActionHandler("openReports", this::openReports);

        // Open Punish menu
        registerActionHandler("openPunish", this::openPunish);
    }

    private Consumer<CirrusPlayerWrapper> getReturnToInspectAction() {
        return player -> new InspectMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                .display(player);
    }

    private void openNotes(Click click) {
        click.clickedMenu().close();
        new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, getReturnToInspectAction())
                .display(click.player());
    }

    private void openAlts(Click click) {
        click.clickedMenu().close();
        new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, getReturnToInspectAction())
                .display(click.player());
    }

    private void openHistory(Click click) {
        click.clickedMenu().close();
        new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, getReturnToInspectAction())
                .display(click.player());
    }

    private void openReports(Click click) {
        click.clickedMenu().close();
        new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, getReturnToInspectAction())
                .display(click.player());
    }

    private void openPunish(Click click) {
        click.clickedMenu().close();
        new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, getReturnToInspectAction())
                .display(click.player());
    }
}
