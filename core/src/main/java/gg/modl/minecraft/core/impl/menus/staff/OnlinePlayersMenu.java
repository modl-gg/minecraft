package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.inspect.InspectMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.util.WebPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class OnlinePlayersMenu extends BaseStaffListMenu<OnlinePlayersMenu.OnlinePlayer> {
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
        public long getTotalPlaytime() { return totalPlaytime; }
        public int getPunishmentCount() { return punishmentCount; }
        public long getSessionDuration() { return System.currentTimeMillis() - sessionStartTime; }
    }

    private List<OnlinePlayer> onlinePlayers = new ArrayList<>();
    private String currentSort = "Recent Reports";
    private final List<String> sortOptions = Arrays.asList("Least Playtime", "Recent Reports", "Longest Session");
    private final String panelUrl;

    public OnlinePlayersMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                             boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction, "Recent Reports", null);
    }

    public OnlinePlayersMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                             boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction,
                             String sortOption, List<OnlinePlayer> existingPlayers) {
        super("Online Players", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.currentSort = sortOption;
        activeTab = StaffTab.ONLINE_PLAYERS;

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
                    long totalPlaytime = player.getTotalPlaytimeMs() != null ? player.getTotalPlaytimeMs() : 0;
                    onlinePlayers.add(new OnlinePlayer(uuid, player.getUsername(), sessionStart, totalPlaytime, 0));
                }
            } else {
                java.util.logging.Logger.getLogger("modl").warning("Online players fetch: success=" + response.isSuccess()
                        + " players=" + (response.getPlayers() != null ? response.getPlayers().size() : "null"));
            }
        }).exceptionally(e -> {
            java.util.logging.Logger.getLogger("modl").warning("Failed to fetch online players: " + e.getMessage());
            return null;
        });
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        items.put(MenuSlots.SORT_BUTTON, MenuItems.sortButton(currentSort, sortOptions)
                .actionHandler("sort"));

        return items;
    }

    @Override
    protected Collection<OnlinePlayer> elements() {
        if (onlinePlayers.isEmpty())
            return Collections.singletonList(new OnlinePlayer(null, null, 0, 0, 0));

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
        if (player.getName() == null)
            return createEmptyPlaceholder("No online players");

        LocaleManager localeManager = platform.getLocaleManager();

        String punishments = player.getPunishmentCount() > 0
                ? "&c" + player.getPunishmentCount()
                : "&a0";

        Map<String, String> placeholders = Map.of(
                "player_name", player.getName(),
                "session_duration", MenuItems.formatDuration(player.getSessionDuration()),
                "total_playtime", MenuItems.formatDuration(player.getTotalPlaytime()),
                "punishments", punishments
        );

        String title = localeManager.getMessage("menus.online_players.title", placeholders);
        List<String> lore = localeManager.getMessageList("menus.online_players.lore", placeholders);

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.translateColorCodes(title)),
                MenuItems.lore(lore)
        );

        if (player.getUuid() != null && platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(player.getUuid());
            if (cachedTexture != null) {
                headItem = headItem.texture(cachedTexture);
            } else {
                final UUID uuid = player.getUuid();
                WebPlayer.get(uuid).thenAccept(wp -> {
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
        if (player.getName() == null) return;

        StaffModeService staffModeService = platform.getStaffModeService();
        if (staffModeService != null && !click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            if (platform.getCache() == null || !platform.getCache().hasPermission(viewerUuid, Permissions.MOD_ACTIONS)) {
                sendMessage(platform.getLocaleManager().getMessage("general.no_permission"));
                return;
            }

            if (player.getUuid() != null && player.getUuid().equals(viewerUuid)) {
                sendMessage(MenuItems.COLOR_RED + "You cannot target yourself");
                return;
            }

            click.clickedMenu().close();

            if (!staffModeService.isInStaffMode(viewerUuid)) {
                staffModeService.enable(viewerUuid);
                AbstractPlayer staffPlayer = platform.getPlayer(viewerUuid);
                String inGameName = staffPlayer != null ? staffPlayer.getName() : "Staff";
                String panelName = platform.getCache() != null ? platform.getCache().getStaffDisplayName(viewerUuid) : null;
                if (panelName == null) panelName = inGameName;
                platform.sendMessage(viewerUuid, platform.getLocaleManager().getMessage("staff_mode.enabled"));
                platform.staffBroadcast(platform.getLocaleManager().getMessage("staff_mode.enabled_broadcast", Map.of(
                        "staff", panelName, "in-game-name", inGameName)));
                BridgeService bridgeService = platform.getBridgeService();
                if (bridgeService != null) {
                    bridgeService.sendStaffModeEnter(viewerUuid.toString(), inGameName, panelName);
                }
            }

            staffModeService.setTarget(viewerUuid, player.getUuid());
            BridgeService bridgeService = platform.getBridgeService();
            if (bridgeService != null) {
                bridgeService.sendTargetRequest(viewerUuid.toString(), player.getUuid().toString());
            }
            sendMessage(MenuItems.COLOR_GREEN + "Now targeting " + MenuItems.COLOR_GOLD + player.getName());
            return;
        }

        click.clickedMenu().close();

        final String sortState = currentSort;
        final List<OnlinePlayer> playerState = new ArrayList<>(onlinePlayers);

        httpClient.getPlayerProfile(player.getUuid()).thenAccept(response -> {
            if (response.getStatus() == 200) {
                new InspectMenu(platform, httpClient, viewerUuid, viewerName, response.getProfile(),
                    p -> new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null,
                        sortState, playerState).display(p))
                    .display(click.player());
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

        registerActionHandler("sort", this::handleSort);

        StaffNavigationHandlers.registerAll(
                (name, handler) -> registerActionHandler(name, handler),
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("openOnlinePlayers", click -> {});
    }

    private void handleSort(Click click) {
        int currentIndex = sortOptions.indexOf(currentSort);
        int nextIndex = (currentIndex + 1) % sortOptions.size();
        String nextSort = sortOptions.get(nextIndex);

        ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction,
                        nextSort, onlinePlayers))
                .handle(click);
    }
}
