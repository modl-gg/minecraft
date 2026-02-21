package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Alts Menu - displays linked/alternate accounts for a player.
 */
public class AltsMenu extends BaseInspectListMenu<Account> {

    private static final Logger logger = Logger.getLogger(AltsMenu.class.getName());
    private List<Account> linkedAccounts = new ArrayList<>();
    private final Consumer<CirrusPlayerWrapper> backAction;

    /**
     * Create a new alts menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("Alts: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.backAction = backAction;
        activeTab = InspectTab.ALTS;

        // Fetch linked accounts
        loadLinkedAccounts();
    }

    private static String getPlayerNameStatic(Account account) {
        if (account.getUsernames() != null && !account.getUsernames().isEmpty()) {
            return account.getUsernames().stream()
                    .max((u1, u2) -> u1.getDate().compareTo(u2.getDate()))
                    .map(Account.Username::getUsername)
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    private void loadLinkedAccounts() {
        // Fetch linked accounts from API synchronously so data is ready when elements() is called
        try {
            var response = httpClient.getLinkedAccounts(targetUuid).join();
            if (response.getStatus() == 200 && response.getLinkedAccounts() != null) {
                linkedAccounts = new ArrayList<>(response.getLinkedAccounts());

                // Batch-fetch textures for uncached alt UUIDs and wait briefly
                if (platform.getCache() != null) {
                    List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
                    for (Account alt : linkedAccounts) {
                        if (alt.getMinecraftUuid() != null && platform.getCache().getSkinTexture(alt.getMinecraftUuid()) == null) {
                            final UUID altUuid = alt.getMinecraftUuid();
                            futures.add(gg.modl.minecraft.core.util.WebPlayer.get(altUuid).thenAccept(wp -> {
                                if (wp != null && wp.valid() && wp.textureValue() != null) {
                                    platform.getCache().cacheSkinTexture(altUuid, wp.textureValue());
                                }
                            }));
                        }
                    }
                    if (!futures.isEmpty()) {
                        try {
                            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to fetch linked accounts for " + targetUuid, e);
        }
    }

    @Override
    protected Collection<Account> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (linkedAccounts.isEmpty()) {
            return Collections.singletonList(new Account(null, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()));
        }
        return linkedAccounts;
    }

    @Override
    protected CirrusItem map(Account alt) {
        LocaleManager locale = platform.getLocaleManager();

        // Handle placeholder for empty list
        if (alt.getMinecraftUuid() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.alts"));
        }

        String altName = getPlayerName(alt);

        // Check punishment status
        List<Punishment> activePunishments = alt.getPunishments().stream()
                .filter(Punishment::isActive)
                .collect(java.util.stream.Collectors.toList());
        long activeCount = activePunishments.size();
        long inactiveCount = alt.getPunishments().size() - activeCount;

        // Determine color based on status
        boolean isBanned = activePunishments.stream().anyMatch(Punishment::isBanType);
        boolean isMuted = activePunishments.stream().anyMatch(Punishment::isMuteType);
        String color;
        if (isBanned) {
            color = "&c"; // Red for banned
        } else if (isMuted) {
            color = "&e"; // Yellow for muted
        } else {
            color = "&a"; // Green for ok
        }

        // Online status
        boolean isOnline = platform.getCache() != null && platform.getCache().isOnline(alt.getMinecraftUuid());

        // Real IP logged
        boolean realIpLogged = !alt.getIpList().isEmpty();

        // First login from earliest username date
        String firstLogin = "Unknown";
        if (!alt.getUsernames().isEmpty()) {
            java.util.Date earliest = alt.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(d -> d != null)
                    .min(java.util.Date::compareTo)
                    .orElse(null);
            if (earliest != null) {
                firstLogin = MenuItems.formatDate(earliest);
            }
        }

        // Last seen or session time
        String lastSeenOrSessionTime = "N/A";
        if (!alt.getUsernames().isEmpty()) {
            java.util.Date latest = alt.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(d -> d != null)
                    .max(java.util.Date::compareTo)
                    .orElse(null);
            if (latest != null) {
                lastSeenOrSessionTime = MenuItems.formatDate(latest);
            }
        }

        // Build punishments section
        StringBuilder punishmentsBuilder = new StringBuilder();
        if (activeCount > 0) {
            // Use punishment_line_active_true format
            List<String> activeLines = locale.getMessageList("menus.alt_item.punishment_line_active_true");
            String activeFormat = locale.getMessage("menus.alt_item.active_punishment_format");

            // Build active punishment list
            StringBuilder activePunishmentList = new StringBuilder();
            for (int i = 0; i < activePunishments.size(); i++) {
                Punishment p = activePunishments.get(i);
                String pId = p.getId() != null ? p.getId() : "?";
                String pDate = p.getIssued() != null ? MenuItems.formatDate(p.getIssued()) : "?";
                String pType = p.getTypeCategory();
                String pRemaining = "Permanent";
                Long duration = p.getDuration();
                if (duration != null && duration > 0 && p.getStarted() != null) {
                    // Use started date (when punishment was enforced) for expiry calculation
                    long expiryTime = p.getStarted().getTime() + duration;
                    long remaining = expiryTime - System.currentTimeMillis();
                    pRemaining = MenuItems.formatDuration(remaining > 0 ? remaining : 0);
                }
                String formattedPunishment = activeFormat
                        .replace("{punishment_id}", pId)
                        .replace("{punishment_date}", pDate)
                        .replace("{punishment_type}", pType)
                        .replace("{punishment_remaining}", pRemaining);
                if (i > 0) {
                    activePunishmentList.append("\n");
                }
                activePunishmentList.append(formattedPunishment);
            }

            for (int i = 0; i < activeLines.size(); i++) {
                String line = activeLines.get(i)
                        .replace("{active_count}", String.valueOf(activeCount))
                        .replace("{active_punishment_list}", activePunishmentList.toString());
                if (i > 0) {
                    punishmentsBuilder.append("\n");
                }
                punishmentsBuilder.append(line);
            }
        } else {
            // Use punishment_line_active_false format
            List<String> inactiveLines = locale.getMessageList("menus.alt_item.punishment_line_active_false");
            for (int i = 0; i < inactiveLines.size(); i++) {
                String line = inactiveLines.get(i)
                        .replace("{inactive_count}", String.valueOf(inactiveCount));
                if (i > 0) {
                    punishmentsBuilder.append("\n");
                }
                punishmentsBuilder.append(line);
            }
        }

        // Build variables map
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("color", color);
        vars.put("player_name", altName);
        vars.put("uuid", alt.getMinecraftUuid().toString());
        vars.put("is_online", isOnline ? "&aYes" : "&cNo");
        vars.put("last_seen_or_session_time", lastSeenOrSessionTime);
        vars.put("real_ip", realIpLogged ? "&aYes" : "&cNo");
        vars.put("first_login", firstLogin);
        vars.put("punishments", punishmentsBuilder.toString());

        // Get lore from locale
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.alt_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            // Handle multi-line punishments section
            if (processed.contains("\n")) {
                for (String subLine : processed.split("\n")) {
                    lore.add(subLine);
                }
            } else {
                lore.add(processed);
            }
        }

        // Get title from locale
        String title = locale.getMessage("menus.alt_item.title", vars);

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );

        // Apply skin texture from cache (pre-fetched in loadLinkedAccounts)
        if (platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(alt.getMinecraftUuid());
            if (cachedTexture != null) {
                headItem = headItem.texture(cachedTexture);
            }
        }

        return headItem;
    }

    @Override
    protected void handleClick(Click click, Account alt) {
        // Handle placeholder - do nothing
        if (alt.getMinecraftUuid() == null) {
            return;
        }

        // Open inspect menu for the alt account with back button to this alts menu
        Consumer<CirrusPlayerWrapper> backToAlts = player ->
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                        .display(player);

        ActionHandlers.openMenu(
                new InspectMenu(platform, httpClient, viewerUuid, viewerName, alt, backToAlts))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Override header navigation handlers - pass backAction to preserve the back button
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openAlts", click -> {
            // Already on alts, do nothing
        });
        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
    }
}
