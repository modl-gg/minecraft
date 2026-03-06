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
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.WebPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class StaffMembersMenu extends BaseStaffListMenu<StaffMembersMenu.StaffMemberEntry> {

    public static class StaffMemberEntry {
        private final String id;
        private final String panelName;
        private final String role;
        private final UUID minecraftUuid;
        private final String minecraftUsername;
        private final Date lastSeen;
        private final Long totalPlaytimeMs;
        private final int punishmentsIssuedCount;
        private final boolean online;
        private final long sessionDuration;

        public StaffMemberEntry(String id, String panelName, String role, UUID minecraftUuid, String minecraftUsername,
                                Date lastSeen, Long totalPlaytimeMs, int punishmentsIssuedCount,
                                boolean online, long sessionDuration) {
            this.id = id;
            this.panelName = panelName;
            this.role = role;
            this.minecraftUuid = minecraftUuid;
            this.minecraftUsername = minecraftUsername;
            this.lastSeen = lastSeen;
            this.totalPlaytimeMs = totalPlaytimeMs;
            this.punishmentsIssuedCount = punishmentsIssuedCount;
            this.online = online;
            this.sessionDuration = sessionDuration;
        }

        public String getId() { return id; }
        public String getPanelName() { return panelName; }
        public String getRole() { return role; }
        public UUID getMinecraftUuid() { return minecraftUuid; }
        public String getMinecraftUsername() { return minecraftUsername; }
        public Date getLastSeen() { return lastSeen; }
        public Long getTotalPlaytimeMs() { return totalPlaytimeMs; }
        public int getPunishmentsIssuedCount() { return punishmentsIssuedCount; }
        public boolean isOnline() { return online; }
        public long getSessionDuration() { return sessionDuration; }
    }

    private List<StaffMemberEntry> staffMembers = new ArrayList<>();
    private String currentFilter = "Online";
    private final List<String> filterOptions = Arrays.asList("Online", "Offline", "All");
    private final String panelUrl;

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

        if (existingMembers != null)
            this.staffMembers = new ArrayList<>(existingMembers);
        else
            fetchStaffMembers();
    }

    private void fetchStaffMembers() {
        Cache cache = platform.getCache();
        httpClient.getStaffList().thenAccept(response -> {
            if (response != null && response.isSuccess() && response.getStaff() != null) {
                staffMembers.clear();
                for (StaffListResponse.StaffEntry entry : response.getStaff()) {
                    UUID uuid = null;
                    if (entry.getMinecraftUuid() != null) {
                        try {
                            uuid = UUID.fromString(entry.getMinecraftUuid());
                        } catch (Exception ignored) {}
                    }

                    boolean online = uuid != null && cache != null && cache.isOnline(uuid);
                    long sessionDuration = online && cache != null ? cache.getSessionDuration(uuid) : 0;

                    staffMembers.add(new StaffMemberEntry(
                            entry.getId(),
                            entry.getUsername(),
                            entry.getRole(),
                            uuid,
                            entry.getMinecraftUsername(),
                            entry.getLastSeen(),
                            entry.getTotalPlaytimeMs(),
                            entry.getPunishmentsIssuedCount(),
                            online,
                            sessionDuration
                    ));

                    if (uuid != null && cache != null && cache.getSkinTexture(uuid) == null) {
                        final UUID staffUuid = uuid;
                        WebPlayer.get(staffUuid).thenAccept(wp -> {
                            if (wp != null && wp.valid() && wp.textureValue() != null) {
                                cache.cacheSkinTexture(staffUuid, wp.textureValue());
                            }
                        });
                    }
                }
            }
        }).exceptionally(e -> null);
    }

    private List<String> buildLore(StaffMemberEntry entry) {
        LocaleManager localeManager = platform.getLocaleManager();

        String lastSeenOrSessionTime;
        if (entry.isOnline())
            lastSeenOrSessionTime = MenuItems.formatDuration(entry.getSessionDuration());
        else
            lastSeenOrSessionTime = entry.getLastSeen() != null ? MenuItems.formatDate(entry.getLastSeen()) : "Never";

        String server = "Unknown";
        if (entry.isOnline() && entry.getMinecraftUuid() != null) {
            String playerServer = platform.getPlayerServer(entry.getMinecraftUuid());
            if (playerServer != null) server = playerServer;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("panel_name", entry.getPanelName() != null ? entry.getPanelName() : "Unknown");
        placeholders.put("role", entry.getRole() != null ? entry.getRole() : "Unknown");
        placeholders.put("minecraft_username", entry.getMinecraftUsername() != null ? entry.getMinecraftUsername() : "Not linked");
        placeholders.put("is_online", entry.isOnline() ? "&aYes" : "&cNo");
        placeholders.put("last_seen_or_session_time", lastSeenOrSessionTime);
        placeholders.put("server", server);
        placeholders.put("playtime", MenuItems.formatDuration(
                entry.isOnline()
                        ? (entry.getTotalPlaytimeMs() != null ? entry.getTotalPlaytimeMs() : 0L) + entry.getSessionDuration()
                        : (entry.getTotalPlaytimeMs() != null ? entry.getTotalPlaytimeMs() : 0L)));
        placeholders.put("punishments_count", String.valueOf(entry.getPunishmentsIssuedCount()));

        return localeManager.getMessageList("menus.staff_members.lore", placeholders);
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
        switch (currentFilter) {
            case "Online":
                filtered = staffMembers.stream().filter(StaffMemberEntry::isOnline).collect(Collectors.toList());
                break;
            case "Offline":
                filtered = staffMembers.stream().filter(e -> !e.isOnline()).collect(Collectors.toList());
                break;
            default:
                filtered = new ArrayList<>(staffMembers);
                break;
        }

        if (filtered.isEmpty())
            return Collections.singletonList(new StaffMemberEntry(null, null, null, null, null, null, null, 0, false, 0));
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
        String title = localeManager.getMessage("menus.staff_members.title", Map.of(
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
                (name, handler) -> registerActionHandler(name, handler),
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
