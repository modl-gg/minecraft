package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.inspect.InspectMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Online Players Menu - displays all online players.
 */
public class OnlinePlayersMenu extends BaseStaffListMenu<OnlinePlayersMenu.OnlinePlayer> {

    // Placeholder OnlinePlayer class since no endpoint exists yet
    public static class OnlinePlayer {
        private final UUID uuid;
        private final String name;
        private final long sessionStartTime;
        private final long totalPlaytime;
        private final int punishmentCount;

        public OnlinePlayer(UUID uuid, String name, long sessionStartTime, long totalPlaytime, int punishmentCount) {
            this.uuid = uuid;
            this.name = name;
            this.sessionStartTime = sessionStartTime;
            this.totalPlaytime = totalPlaytime;
            this.punishmentCount = punishmentCount;
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public long getSessionStartTime() { return sessionStartTime; }
        public long getTotalPlaytime() { return totalPlaytime; }
        public int getPunishmentCount() { return punishmentCount; }
        public long getSessionDuration() { return System.currentTimeMillis() - sessionStartTime; }
    }

    private List<OnlinePlayer> onlinePlayers = new ArrayList<>();
    private String currentSort = "Recent Reports";
    private final List<String> sortOptions = Arrays.asList("Least Playtime", "Recent Reports", "Longest Session");
    private final String panelUrl;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    /**
     * Create a new online players menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param backAction Action to return to parent menu
     */
    public OnlinePlayersMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                             boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction, "Recent Reports", null);
    }

    /**
     * Create a new online players menu with preserved state.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param backAction Action to return to parent menu
     * @param sortOption Current sort option to preserve
     * @param existingPlayers Existing player list to preserve (if null, will fetch)
     */
    public OnlinePlayersMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                             boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction,
                             String sortOption, List<OnlinePlayer> existingPlayers) {
        super("Online Players", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        this.currentSort = sortOption;
        activeTab = StaffTab.ONLINE_PLAYERS;

        // Use existing players if provided, otherwise fetch
        if (existingPlayers != null) {
            this.onlinePlayers = new ArrayList<>(existingPlayers);
        } else {
            fetchOnlinePlayers();
        }
    }

    private void fetchOnlinePlayers() {
        httpClient.getOnlinePlayers().thenAccept(response -> {
            if (response.isSuccess() && response.getPlayers() != null) {
                onlinePlayers.clear();
                for (var player : response.getPlayers()) {
                    UUID uuid = null;
                    try {
                        uuid = UUID.fromString(player.getUuid());
                    } catch (Exception ignored) {}

                    long sessionStart = player.getJoinedAt() != null ? player.getJoinedAt().getTime() : System.currentTimeMillis();
                    onlinePlayers.add(new OnlinePlayer(uuid, player.getUsername(), sessionStart, 0, 0));
                }
            } else {
                System.err.println("[MODL] Online players fetch: success=" + response.isSuccess()
                        + " players=" + (response.getPlayers() != null ? response.getPlayers().size() : "null"));
            }
        }).exceptionally(e -> {
            System.err.println("[MODL] Failed to fetch online players: " + e.getMessage());
            return null;
        });
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add sort button at slot 40 (y position in navigation row)
        items.put(MenuSlots.SORT_BUTTON, MenuItems.sortButton(currentSort, sortOptions)
                .actionHandler("sort"));

        return items;
    }

    @Override
    protected Collection<OnlinePlayer> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (onlinePlayers.isEmpty()) {
            return Collections.singletonList(new OnlinePlayer(null, null, 0, 0, 0));
        }

        // Sort players based on current sort option
        List<OnlinePlayer> sorted = new ArrayList<>(onlinePlayers);

        switch (currentSort) {
            case "Least Playtime":
                sorted.sort((p1, p2) -> Long.compare(p1.getTotalPlaytime(), p2.getTotalPlaytime()));
                break;
            case "Longest Session":
                sorted.sort((p1, p2) -> Long.compare(p2.getSessionDuration(), p1.getSessionDuration()));
                break;
            case "Recent Reports":
            default:
                // Would sort by recent reports if data available
                break;
        }

        return sorted;
    }

    @Override
    protected CirrusItem map(OnlinePlayer player) {
        // Handle placeholder for empty list
        if (player.getName() == null) {
            return createEmptyPlaceholder("No online players");
        }

        List<String> lore = new ArrayList<>();

        // Session time
        lore.add(MenuItems.COLOR_GRAY + "Session: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(player.getSessionDuration()));

        // Total playtime
        lore.add(MenuItems.COLOR_GRAY + "Total Playtime: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(player.getTotalPlaytime()));

        // Punishments
        if (player.getPunishmentCount() > 0) {
            lore.add(MenuItems.COLOR_GRAY + "Punishments: " + MenuItems.COLOR_RED + player.getPunishmentCount());
        } else {
            lore.add(MenuItems.COLOR_GRAY + "Punishments: " + MenuItems.COLOR_GREEN + "0");
        }

        // TODO: Add recent reports when data available

        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Click to inspect player");

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + player.getName()),
                MenuItems.lore(lore)
        );

        // Apply skin texture from cache if available
        if (player.getUuid() != null && platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(player.getUuid());
            if (cachedTexture != null) {
                headItem = headItem.texture(cachedTexture);
            } else {
                // Async fire-and-forget to populate cache for next menu open
                final UUID uuid = player.getUuid();
                gg.modl.minecraft.core.util.WebPlayer.get(uuid).thenAccept(wp -> {
                    if (wp != null && wp.valid() && wp.textureValue() != null) {
                        platform.getCache().cacheSkinTexture(uuid, wp.textureValue());
                    }
                });
            }
        }

        return headItem;
    }

    @Override
    protected void handleClick(Click click, OnlinePlayer player) {
        // Handle placeholder - do nothing
        if (player.getName() == null) {
            return;
        }

        // Fetch player profile and open inspect menu
        click.clickedMenu().close();

        // Capture current state for back action
        final String sortState = currentSort;
        final List<OnlinePlayer> playerState = new ArrayList<>(onlinePlayers);

        httpClient.getPlayerProfile(player.getUuid()).thenAccept(response -> {
            if (response.getStatus() == 200) {
                platform.runOnMainThread(() -> {
                    // Opening InspectMenu from Staff menu - back button returns to OnlinePlayersMenu with preserved state
                    new InspectMenu(platform, httpClient, viewerUuid, viewerName, response.getProfile(),
                            p -> new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null,
                                    sortState, playerState).display(p))
                            .display(click.player());
                });
            } else {
                sendMessage(MenuItems.COLOR_RED + "Failed to load player profile");
            }
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to load player profile: " + e.getMessage());
            return null;
        });
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Sort handler
        registerActionHandler("sort", this::handleSort);

        // Override header navigation - primary tabs should NOT pass backAction
        registerActionHandler("openOnlinePlayers", click -> {
            // Already here, do nothing
        });

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPunishments", ActionHandlers.openMenu(
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openTickets", ActionHandlers.openMenu(
                new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPanel", click -> {
            click.clickedMenu().close();
            String escapedUrl = panelUrl.replace("\"", "\\\"");
            String panelJson = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"Staff Panel: \",\"color\":\"gold\"}," +
                "{\"text\":\"%s\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to open in browser\"}}]}",
                escapedUrl, panelUrl
            );
            platform.sendJsonMessage(viewerUuid, panelJson);
        });

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));
    }

    private void handleSort(Click click) {
        // Cycle through sort options
        int currentIndex = sortOptions.indexOf(currentSort);
        int nextIndex = (currentIndex + 1) % sortOptions.size();
        String nextSort = sortOptions.get(nextIndex);

        // Refresh menu - preserve backAction and existing data
        ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction,
                        nextSort, onlinePlayers))
                .handle(click);
    }
}
