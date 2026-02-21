package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentPreviewResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.StandingGuiConfig;
import gg.modl.minecraft.core.impl.menus.base.BaseListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.*;

/**
 * Player-facing menu showing their own social/gameplay standing and punishment history.
 * Extends BaseListMenu for proper AbstractBrowser pagination.
 *
 * Layout (6 rows):
 * <pre>
 * Row 0: blank
 * Row 1: * * S * * * G * *   (social at 11, gameplay at 15)
 * Row 2: blank
 * Row 3: * [punishment items 28-34]  *
 * Row 4: * * < * * * > * *   (pagination)
 * Row 5: blank
 * </pre>
 */
public class StandingMenu extends BaseListMenu<Punishment> {

    private static final int SOCIAL_SLOT = 11;
    private static final int GAMEPLAY_SLOT = 15;

    private final Account account;
    private final PunishmentPreviewResponse previewData;
    private final StandingGuiConfig guiConfig;
    private final Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal;
    private final List<Punishment> punishments;

    public StandingMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                        Account account, PunishmentPreviewResponse previewData,
                        StandingGuiConfig guiConfig,
                        Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal) {
        super(MenuItems.translateColorCodes(guiConfig.getTitle()), platform, httpClient,
                viewerUuid, viewerName, null);
        this.account = account;
        this.previewData = previewData;
        this.guiConfig = guiConfig;
        this.typesByOrdinal = typesByOrdinal;

        // Filter out kicks and sort newest first
        this.punishments = new ArrayList<>();
        for (Punishment p : account.getPunishments()) {
            if (!p.isKickType()) {
                punishments.add(p);
            }
        }
        punishments.sort((a, b) -> b.getIssued().compareTo(a.getIssued()));
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Social status item in header row
        items.put(SOCIAL_SLOT, buildStatusItem(
                guiConfig.getSocialItem(),
                guiConfig.getSocialTitle(),
                previewData != null ? previewData.getSocialStatus() : "Unknown",
                previewData != null ? previewData.getSocialPoints() : 0,
                guiConfig.getSocialDescriptions()
        ));

        // Gameplay status item in header row
        items.put(GAMEPLAY_SLOT, buildStatusItem(
                guiConfig.getGameplayItem(),
                guiConfig.getGameplayTitle(),
                previewData != null ? previewData.getGameplayStatus() : "Unknown",
                previewData != null ? previewData.getGameplayPoints() : 0,
                guiConfig.getGameplayDescriptions()
        ));

        return items;
    }

    @Override
    protected Collection<Punishment> elements() {
        if (punishments.isEmpty()) {
            return Collections.singletonList(new Punishment());
        }
        return punishments;
    }

    @Override
    protected CirrusItem map(Punishment punishment) {
        // Handle placeholder for empty list
        if (punishment.getId() == null || punishment.getId().isEmpty()) {
            return createEmptyPlaceholder("No punishments");
        }

        String id = punishment.getId();
        String date = MenuItems.formatDate(punishment.getIssued());
        String typeName = getTypeName(punishment);
        int ordinal = punishment.getTypeOrdinal();

        // Duration
        Long effectiveDuration = getEffectiveDuration(punishment);
        String duration;
        if (effectiveDuration == null || effectiveDuration <= 0) {
            duration = "Permanent";
        } else {
            duration = MenuItems.formatDuration(effectiveDuration);
        }

        // Type category (Ban/Mute) using registry-based detection
        String typeCategory;
        if (punishment.isBanType()) {
            typeCategory = "ban";
        } else if (punishment.isMuteType()) {
            typeCategory = "mute";
        } else {
            typeCategory = punishment.getTypeCategory();
        }

        // Status string
        String status = buildStatusString(punishment, effectiveDuration);

        // Reason - use player description from punishment type (same as ban/mute screen)
        String reason = getPlayerDescription(punishment);

        // Build title from config template
        Map<String, String> vars = new HashMap<>();
        vars.put("id", id);
        vars.put("date", date);
        vars.put("duration", duration);
        vars.put("type", typeCategory);
        vars.put("type_name", typeName);
        vars.put("status", status);
        vars.put("reason", reason);

        String title = guiConfig.getPunishmentTitle();
        List<String> loreTemplate = guiConfig.getPunishmentLore();

        for (Map.Entry<String, String> entry : vars.entrySet()) {
            title = title.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        List<String> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            lore.add(processed);
        }

        // Item type: sword for ban, paper for mute
        CirrusItemType itemType;
        if (punishment.isBanType()) {
            itemType = CirrusItemType.DIAMOND_SWORD;
        } else {
            itemType = CirrusItemType.PAPER;
        }

        return CirrusItem.of(itemType, CirrusChatElement.ofLegacyText(MenuItems.translateColorCodes(title)),
                MenuItems.lore(lore));
    }

    @Override
    protected void handleClick(Click click, Punishment punishment) {
        // Player-facing menu - no action on click
    }

    private CirrusItem buildStatusItem(String itemId, String titleTemplate, String status, int points,
                                       Map<String, List<String>> descriptions) {
        String displayStatus = guiConfig.getStatusDisplayName(status);
        String title = titleTemplate
                .replace("{status}", displayStatus)
                .replace("{points}", String.valueOf(points));

        // Find matching description - try exact match first, then case-insensitive
        List<String> desc = null;
        if (status != null && descriptions != null) {
            desc = descriptions.get(status);
            if (desc == null) {
                desc = descriptions.get(status.toLowerCase());
            }
            if (desc == null) {
                for (Map.Entry<String, List<String>> entry : descriptions.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(status)) {
                        desc = entry.getValue();
                        break;
                    }
                }
            }
        }

        List<String> lore = new ArrayList<>();
        if (desc != null) {
            for (String line : desc) {
                lore.add(line
                        .replace("{status}", displayStatus)
                        .replace("{points}", String.valueOf(points)));
            }
        }

        CirrusItemType type = CirrusItemType.of(itemId);
        return CirrusItem.of(type, CirrusChatElement.ofLegacyText(MenuItems.translateColorCodes(title)),
                MenuItems.lore(lore));
    }

    private String buildStatusString(Punishment punishment, Long effectiveDuration) {
        Date pardonDate = findPardonDate(punishment);
        if (pardonDate != null) {
            long ago = System.currentTimeMillis() - pardonDate.getTime();
            return "pardoned " + MenuItems.formatDuration(ago > 0 ? ago : 0) + " ago";
        }

        if (punishment.getStarted() == null) {
            return "queued";
        }

        boolean active = isPunishmentActive(punishment, effectiveDuration);
        if (active) {
            if (effectiveDuration == null || effectiveDuration <= 0) {
                return "permanent";
            }
            long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
            long remaining = expiryTime - System.currentTimeMillis();
            return "expires in " + MenuItems.formatDuration(remaining > 0 ? remaining : 0);
        } else {
            if (effectiveDuration != null && effectiveDuration > 0 && punishment.getStarted() != null) {
                long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
                long ago = System.currentTimeMillis() - expiryTime;
                return "expired " + MenuItems.formatDuration(ago > 0 ? ago : 0) + " ago";
            }
            return "expired";
        }
    }

    private String getTypeName(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        if (typeData != null && typeData.getName() != null) {
            return typeData.getName();
        }
        Object typeName = punishment.getDataMap().get("typeName");
        if (typeName instanceof String && !((String) typeName).isEmpty()) {
            return (String) typeName;
        }
        return punishment.getTypeCategory();
    }

    /**
     * Get the player-facing description for the punishment type.
     * This is the same message shown on ban/mute screens.
     */
    private String getPlayerDescription(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        if (typeData != null && typeData.getPlayerDescription() != null && !typeData.getPlayerDescription().isEmpty()) {
            return typeData.getPlayerDescription();
        }
        // Fallback to reason from punishment data
        return punishment.getReason();
    }

    private Date findPardonDate(Punishment punishment) {
        for (Modification mod : punishment.getModifications()) {
            if (mod.getType() == Modification.Type.MANUAL_PARDON ||
                    mod.getType() == Modification.Type.APPEAL_ACCEPT) {
                return mod.getIssued();
            }
        }
        return null;
    }

    private Long getEffectiveDuration(Punishment punishment) {
        Long duration = punishment.getDuration();
        for (Modification mod : punishment.getModifications()) {
            if (mod.getType() == Modification.Type.MANUAL_DURATION_CHANGE ||
                    mod.getType() == Modification.Type.APPEAL_DURATION_CHANGE) {
                Long modDuration = mod.getEffectiveDuration();
                duration = (modDuration == null || modDuration <= 0) ? null : modDuration;
            }
        }
        return duration;
    }

    private boolean isPunishmentActive(Punishment punishment, Long effectiveDuration) {
        if (findPardonDate(punishment) != null) return false;
        if (!punishment.isActive()) return false;
        if (punishment.getStarted() == null) return true;
        if (effectiveDuration == null || effectiveDuration <= 0) return true;
        long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
        return System.currentTimeMillis() < expiryTime;
    }
}
