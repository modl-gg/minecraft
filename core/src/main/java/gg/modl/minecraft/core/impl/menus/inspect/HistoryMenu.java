package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * History Menu - displays punishment history for a player.
 */
public class HistoryMenu extends BaseInspectListMenu<Punishment> {

    private final Consumer<CirrusPlayerWrapper> backAction;
    private final Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();

    /**
     * Create a new history menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public HistoryMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("History: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.backAction = backAction;
        activeTab = InspectTab.HISTORY;

        // Load punishment types for proper type name display
        loadPunishmentTypes();
    }

    private void loadPunishmentTypes() {
        try {
            httpClient.getPunishmentTypes().thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    for (PunishmentTypesResponse.PunishmentTypeData type : response.getData()) {
                        typesByOrdinal.put(type.getOrdinal(), type);
                    }
                }
            }).join();
        } catch (Exception e) {
            // Failed to load - will use fallback type detection
        }
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

    @Override
    protected Collection<Punishment> elements() {
        // Punishments are stored in the account object
        List<Punishment> punishments = new ArrayList<>(targetAccount.getPunishments());

        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (punishments.isEmpty()) {
            return Collections.singletonList(new Punishment());
        }

        // Sort by date, newest first
        punishments.sort((p1, p2) -> p2.getIssued().compareTo(p1.getIssued()));
        return punishments;
    }

    @Override
    protected CirrusItem map(Punishment punishment) {
        LocaleManager locale = platform.getLocaleManager();

        // Handle placeholder for empty list
        if (punishment.getId() == null || punishment.getId().isEmpty()) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.history"));
        }

        // Type and status - get type name from loaded punishment types
        String typeName = getTypeName(punishment);
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        boolean isKick = typeData != null && typeData.isKick();
        boolean isBan = typeData != null && typeData.isBan();
        boolean isMute = typeData != null && typeData.isMute();

        // Get effective duration (considering modifications)
        Long effectiveDuration = getEffectiveDuration(punishment);

        // Check if punishment is truly active (considering duration modifications and pardons)
        boolean isActive = !isKick && isPunishmentEffectivelyActive(punishment, effectiveDuration);

        // Calculate initial duration (empty for kicks)
        String initialDuration = "";
        if (!isKick) {
            Long duration = punishment.getDuration();
            if (duration == null || duration <= 0) {
                initialDuration = "Permanent";
            } else {
                initialDuration = MenuItems.formatDuration(duration);
            }
        }

        // Determine type category for title
        String spaceBanMuteOrKick = "";
        if (isKick) {
            spaceBanMuteOrKick = "Kick";
        } else if (isBan) {
            spaceBanMuteOrKick = " Ban";
        } else if (isMute) {
            spaceBanMuteOrKick = " Mute";
        }

        // Build status line
        String statusLine;
        // Check for pardon first (pardoned-but-unstarted should show "Pardoned", not "Not started")
        Date pardonDate = isKick ? null : findPardonDate(punishment);
        if (isKick) {
            statusLine = ""; // Don't show status for kicks
        } else if (pardonDate != null) {
            long pardonedAgo = System.currentTimeMillis() - pardonDate.getTime();
            String pardonedFormatted = MenuItems.formatDuration(pardonedAgo > 0 ? pardonedAgo : 0);
            statusLine = locale.getMessage("menus.history_item.status_pardoned",
                    Map.of("pardoned", pardonedFormatted));
        } else if (punishment.getStarted() == null) {
            // Punishment not yet started
            statusLine = locale.getMessage("menus.history_item.status_unstarted");
        } else if (isActive) {
            if (effectiveDuration == null || effectiveDuration <= 0) {
                // Permanent
                statusLine = locale.getMessage("menus.history_item.status_permanent");
            } else {
                // Calculate remaining time using effective duration
                long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
                long remaining = expiryTime - System.currentTimeMillis();
                String expiryFormatted = MenuItems.formatDuration(remaining > 0 ? remaining : 0);
                statusLine = locale.getMessage("menus.history_item.status_active",
                        Map.of("expiry", expiryFormatted));
            }
        } else {
            // Naturally expired - calculate time since expired using effective duration
            if (effectiveDuration != null && effectiveDuration > 0 && punishment.getStarted() != null) {
                long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
                long expiredAgo = System.currentTimeMillis() - expiryTime;
                String expiredFormatted = MenuItems.formatDuration(expiredAgo > 0 ? expiredAgo : 0);
                statusLine = locale.getMessage("menus.history_item.status_inactive",
                        Map.of("expired", expiredFormatted));
            } else {
                statusLine = locale.getMessage("menus.history_item.status_inactive",
                        Map.of("expired", "N/A"));
            }
        }

        // Build notes section - each note on a new line using note_format
        StringBuilder notesBuilder = new StringBuilder();
        List<Note> notes = punishment.getNotes();
        if (notes != null && !notes.isEmpty()) {
            String noteFormat = locale.getMessage("menus.history_item.note_format");
            for (int i = 0; i < notes.size(); i++) {
                Note note = notes.get(i);
                String noteDate = MenuItems.formatDate(note.getDate());
                String noteIssuer = note.getIssuerName();
                String noteText = note.getText();
                String formattedNote = noteFormat
                        .replace("{note_date}", noteDate)
                        .replace("{note_issuer}", noteIssuer)
                        .replace("{note}", noteText);
                if (i > 0) {
                    notesBuilder.append("\n");
                }
                notesBuilder.append(formattedNote);
            }
        }

        // Build variables map using HashMap to allow more than 10 entries
        Map<String, String> vars = new HashMap<>();
        vars.put("punishment_id", punishment.getId() != null ? punishment.getId() : "Unknown");
        vars.put("punishment_type", typeName);
        vars.put("initial_duration_if_not_kick", initialDuration);
        vars.put("space_ban_mute_or_kick", spaceBanMuteOrKick);
        vars.put("status_line", statusLine);
        vars.put("notes", notesBuilder.toString());
        vars.put("reason", punishment.getReason() != null ? punishment.getReason() : "No reason");
        vars.put("issuer", punishment.getIssuerName() != null ? punishment.getIssuerName() : "Unknown");
        vars.put("issued_date", MenuItems.formatDate(punishment.getIssued()));
        Object issuedServerObj = punishment.getDataMap().get("issuedServer");
        vars.put("issued_server", issuedServerObj instanceof String ? (String) issuedServerObj : "");

        // Get lore from locale
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.history_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            // Handle {notes} which may contain newlines - split into multiple lore lines
            if (processed.contains("\n")) {
                for (String subLine : processed.split("\n")) {
                    lore.add(subLine);
                }
            } else if (!processed.isEmpty()) {
                lore.add(processed);
            }
        }

        // Get title from locale
        String titleKey = isActive ? "menus.history_item.title_active" : "menus.history_item.title_inactive";
        String title = locale.getMessage(titleKey, vars);

        // Get appropriate item type based on punishment type
        CirrusItemType itemType = getPunishmentItemType(punishment);

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private String getTypeName(Punishment punishment) {
        // First try to get from our loaded punishment types
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        if (typeData != null && typeData.getName() != null) {
            return typeData.getName();
        }

        // Fall back to dataMap typeName
        Object typeName = punishment.getDataMap().get("typeName");
        if (typeName instanceof String && !((String) typeName).isEmpty()) {
            return (String) typeName;
        }

        // Last resort - use category detection
        return punishment.getTypeCategory();
    }

    private CirrusItemType getPunishmentItemType(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();

        // Check config-defined item mapping first
        gg.modl.minecraft.core.config.PunishGuiConfig guiConfig = getOrLoadGuiConfig();
        if (guiConfig != null) {
            String itemId = guiConfig.getItemForOrdinal(ordinal);
            if (itemId != null) {
                return CirrusItemType.of(itemId);
            }
        }

        // Fall back to registry-based detection
        if (punishment.isBanType()) {
            return CirrusItemType.BARRIER;
        } else if (punishment.isMuteType()) {
            return CirrusItemType.PAPER;
        } else if (punishment.isKickType()) {
            return CirrusItemType.LEATHER_BOOTS;
        }
        return CirrusItemType.PAPER;
    }

    private gg.modl.minecraft.core.config.PunishGuiConfig getOrLoadGuiConfig() {
        gg.modl.minecraft.core.impl.cache.Cache cache = platform.getCache();
        if (cache == null) return null;
        gg.modl.minecraft.core.config.PunishGuiConfig config = cache.getCachedPunishGuiConfig();
        if (config == null) {
            config = gg.modl.minecraft.core.config.PunishGuiConfig.load(
                    platform.getDataFolder().toPath(),
                    java.util.logging.Logger.getLogger("MODL"));
            cache.cachePunishGuiConfig(config);
        }
        return config;
    }

    @Override
    protected void handleClick(Click click, Punishment punishment) {
        // Handle placeholder - do nothing
        if (punishment.getId() == null || punishment.getId().isEmpty()) {
            return;
        }

        // Open modify punishment menu - this is a secondary menu, back button returns to HistoryMenu
        // Pass backAction as rootBackAction so primary tab navigation preserves the back button
        ActionHandlers.openMenu(
                new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishment, backAction,
                        p -> new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction).display(p)))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Override header navigation handlers - pass backAction to preserve the back button
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openHistory", click -> {
            // Already on history, do nothing
        });
        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
    }

    /**
     * Find the date when a punishment was pardoned by looking for MANUAL_PARDON or APPEAL_ACCEPT modifications.
     * @param punishment The punishment to check
     * @return The pardon date, or null if not pardoned
     */
    private Date findPardonDate(Punishment punishment) {
        List<Modification> modifications = punishment.getModifications();
        if (modifications == null || modifications.isEmpty()) {
            return null;
        }

        // Look for pardon modifications
        for (Modification mod : modifications) {
            if (mod.getType() == Modification.Type.MANUAL_PARDON ||
                mod.getType() == Modification.Type.APPEAL_ACCEPT) {
                return mod.getIssued();
            }
        }
        return null;
    }

    /**
     * Get the effective duration of a punishment, considering any duration change modifications.
     * @param punishment The punishment to check
     * @return The effective duration in milliseconds, or null for permanent
     */
    private Long getEffectiveDuration(Punishment punishment) {
        List<Modification> modifications = punishment.getModifications();
        if (modifications == null || modifications.isEmpty()) {
            return punishment.getDuration();
        }

        // Look for the most recent duration change modification
        Long effectiveDuration = punishment.getDuration();
        for (Modification mod : modifications) {
            if (mod.getType() == Modification.Type.MANUAL_DURATION_CHANGE ||
                mod.getType() == Modification.Type.APPEAL_DURATION_CHANGE) {
                // Get effective duration from modification (null or <= 0 means permanent)
                Long modDuration = mod.getEffectiveDuration();
                if (modDuration == null || modDuration <= 0) {
                    effectiveDuration = null;
                } else {
                    effectiveDuration = modDuration;
                }
            }
        }
        return effectiveDuration;
    }

    /**
     * Check if a punishment is effectively active, considering duration modifications and pardons.
     * @param punishment The punishment to check
     * @param effectiveDuration The effective duration (from getEffectiveDuration)
     * @return True if the punishment is effectively active
     */
    private boolean isPunishmentEffectivelyActive(Punishment punishment, Long effectiveDuration) {
        // First check if it was pardoned
        if (findPardonDate(punishment) != null) {
            return false;
        }

        // Check if the data.active flag is false
        if (!punishment.isActive()) {
            return false;
        }

        // Check if it has a started date
        if (punishment.getStarted() == null) {
            return true; // Not started yet, considered active
        }

        // If permanent (null or <= 0 duration), it's active
        if (effectiveDuration == null || effectiveDuration <= 0) {
            return true;
        }

        // Check if the effective duration has expired
        long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
        return System.currentTimeMillis() < expiryTime;
    }
}
