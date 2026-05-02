package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.StaffListResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public class StaffMembersMenu extends BaseStaffListMenu<StaffMembersMenu.StaffMemberEntry> {
    @Getter
    public static class StaffMemberEntry {
        private final String id, panelName, role;
        private final UUID minecraftUuid;
        private final String minecraftUsername, lastServer;
        private final Date lastSeen;
        private final Long totalPlaytimeMs;
        private final int punishmentsIssuedCount;
        private final boolean online;
        private final long sessionDuration;

        public StaffMemberEntry(String id, String panelName, String role, UUID minecraftUuid, String minecraftUsername,
                                String lastServer, Date lastSeen, Long totalPlaytimeMs, int punishmentsIssuedCount,
                                boolean online, long sessionDuration) {
            this.id = id;
            this.panelName = panelName;
            this.role = role;
            this.minecraftUuid = minecraftUuid;
            this.minecraftUsername = minecraftUsername;
            this.lastServer = lastServer;
            this.lastSeen = lastSeen;
            this.totalPlaytimeMs = totalPlaytimeMs;
            this.punishmentsIssuedCount = punishmentsIssuedCount;
            this.online = online;
            this.sessionDuration = sessionDuration;
        }

    }

    private List<StaffMemberEntry> staffMembers = new ArrayList<>();
    private String currentFilter;
    private final List<String> filterOptions = Arrays.asList("Online", "Offline", "All");
    private final String panelUrl;
    @Getter private CompletableFuture<Void> dataFuture;

    public StaffMembersMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                            boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction, "Online", null);
    }

    public StaffMembersMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                            boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction,
                            String filter, List<StaffMemberEntry> existingMembers) {
        super("Staff Members", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.currentFilter = filter;
        activeTab = StaffTab.SETTINGS;

        if (existingMembers != null) {
            this.staffMembers = new ArrayList<>(existingMembers);
            this.dataFuture = CompletableFuture.completedFuture(null);
        } else
            this.dataFuture = fetchStaffMembers();
    }

    private CompletableFuture<Void> fetchStaffMembers() {
        Cache cache = platform.getCache();
        return httpClient.getStaffList().thenAccept(response -> {
            if (response != null && response.isSuccess() && response.getStaff() != null) {
                staffMembers.clear();
                for (StaffListResponse.StaffEntry entry : response.getStaff()) {
                    UUID uuid = null;
                    if (entry.getMinecraftUuid() != null) {
                        try {
                            uuid = UUID.fromString(entry.getMinecraftUuid());
                        } catch (Exception ignored) {}
                    }

                    CachedProfile staffProfile = uuid != null && cache != null ? cache.getPlayerProfile(uuid) : null;
                    boolean online = staffProfile != null;
                    long sessionDuration = online ? staffProfile.getSessionDuration() : 0;

                    staffMembers.add(new StaffMemberEntry(
                            entry.getId(),
                            entry.getUsername(),
                            entry.getRole(),
                            uuid,
                            entry.getMinecraftUsername(),
                            entry.getLastServer(),
                            entry.getLastSeen(),
                            entry.getTotalPlaytimeMs(),
                            entry.getPunishmentsIssuedCount(),
                            online,
                            sessionDuration
                    ));

                    if (uuid != null && cache != null && cache.getSkinTexture(uuid) == null) {
                        final UUID staffUuid = uuid;
                        WebPlayer.get(staffUuid).thenAccept(wp -> {
                            if (wp != null && wp.isValid() && wp.getTextureValue() != null) {
                                cache.cacheSkinTexture(staffUuid, wp.getTextureValue());
                            }
                        });
                    }
                }
            }
        }).exceptionally(e -> null);
    }

    private List<String> buildLore(StaffMemberEntry entry) {
        LocaleManager localeManager = platform.getLocaleManager();
        Map<String, String> placeholders = buildLorePlaceholders(entry);

        return localeManager.getMessageList("menus.staff_members.lore", placeholders);
    }

    private String resolveLastSeenOrSessionTime(StaffMemberEntry entry) {
        if (entry.isOnline())
            return MenuItems.formatDuration(entry.getSessionDuration());
        return entry.getLastSeen() != null ? MenuItems.formatDate(entry.getLastSeen()) : "Never";
    }

    private String resolveServer(StaffMemberEntry entry) {
        String server = "Unknown";
        if (entry.isOnline() && entry.getMinecraftUuid() != null) {
            String playerServer = platform.getPlayerServer(entry.getMinecraftUuid());
            if (playerServer != null) server = playerServer;
        } else if (entry.getLastServer() != null) {
            server = entry.getLastServer();
        }
        return server;
    }

    private Map<String, String> buildLorePlaceholders(StaffMemberEntry entry) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("panel_name", entry.getPanelName() != null ? entry.getPanelName() : "Unknown");
        placeholders.put("role", entry.getRole() != null ? entry.getRole() : "Unknown");
        placeholders.put("minecraft_username", entry.getMinecraftUsername() != null ? entry.getMinecraftUsername() : "Not linked");
        placeholders.put("is_online", entry.isOnline() ? "&aYes" : "&cNo");
        placeholders.put("last_seen_or_session_time", resolveLastSeenOrSessionTime(entry));
        placeholders.put("server", resolveServer(entry));
        placeholders.put("playtime", MenuItems.formatDuration(
                entry.isOnline()
                        ? (entry.getTotalPlaytimeMs() != null ? entry.getTotalPlaytimeMs() : 0L) + entry.getSessionDuration()
                        : (entry.getTotalPlaytimeMs() != null ? entry.getTotalPlaytimeMs() : 0L)));
        placeholders.put("punishments_count", String.valueOf(entry.getPunishmentsIssuedCount()));

        return placeholders;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions)
                .actionHandler("filter"));

        return items;
    }

    @Override
    protected Collection<StaffMemberEntry> elements() {
        List<StaffMemberEntry> filtered;
        if ("Online".equals(currentFilter)) {
            filtered = staffMembers.stream().filter(StaffMemberEntry::isOnline).collect(Collectors.toList());
        } else if ("Offline".equals(currentFilter)) {
            filtered = staffMembers.stream().filter(e -> !e.isOnline()).collect(Collectors.toList());
        } else {
            filtered = new ArrayList<>(staffMembers);
        }

        if (filtered.isEmpty())
            return Collections.singletonList(new StaffMemberEntry(null, null, null, null, null, null, null, null, 0, false, 0));
        return filtered;
    }

    @Override
    protected CirrusItem map(StaffMemberEntry entry) {
        if (entry.getPanelName() == null) {
            String emptyMsg = "Online".equals(currentFilter) ? "No staff online" :
                    "Offline".equals(currentFilter) ? "No staff offline" : "No staff members";
            return createEmptyPlaceholder(emptyMsg);
        }

        List<String> lore = buildLore(entry);

        LocaleManager localeManager = platform.getLocaleManager();
        String title = localeManager.getMessage("menus.staff_members.title", mapOf(
                "panel_name", entry.getPanelName() != null ? entry.getPanelName() : "Unknown"
        ));

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.translateColorCodes(title)),
                MenuItems.lore(lore)
        );

        if (entry.getMinecraftUuid() != null && platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(entry.getMinecraftUuid());
            if (cachedTexture != null)
                headItem = headItem.texture(cachedTexture);
        }

        return headItem;
    }

    @Override
    protected void handleClick(Click click, StaffMemberEntry entry) {
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("filter", this::handleFilter);

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);
    }

    private void handleFilter(Click click) {
        int currentIndex = filterOptions.indexOf(currentFilter);
        int nextIndex = (currentIndex + 1) % filterOptions.size();
        String nextFilter = filterOptions.get(nextIndex);

        ActionHandlers.openMenu(
                new StaffMembersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction,
                        nextFilter, staffMembers))
                .handle(click);
    }
}
