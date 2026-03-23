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
import gg.modl.minecraft.core.config.ConfigManager.StandingGuiConfig;
import gg.modl.minecraft.core.impl.menus.base.BaseListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

public class StandingMenu extends BaseListMenu<Punishment> {
    private static final int SOCIAL_SLOT = 11, GAMEPLAY_SLOT = 15;

    private final PunishmentPreviewResponse previewData;
    private final StandingGuiConfig guiConfig;
    private final LocaleManager localeManager;
    private final Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal;
    private final List<Punishment> punishments;

    public StandingMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                        Account account, PunishmentPreviewResponse previewData,
                        StandingGuiConfig guiConfig, LocaleManager localeManager,
                        Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal) {
        super(MenuItems.translateColorCodes(localeManager.getMessage("standing_gui.title")), platform, httpClient,
                viewerUuid, viewerName, null);
        this.previewData = previewData;
        this.guiConfig = guiConfig;
        this.localeManager = localeManager;
        this.typesByOrdinal = typesByOrdinal;

        this.punishments = new ArrayList<>();
        for (Punishment p : account.getPunishments())
            if (!p.isKickType())
                punishments.add(p);
        punishments.sort((a, b) -> b.getIssued().compareTo(a.getIssued()));
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        String socialStatus = previewData != null ? previewData.getSocialStatus() : "Unknown";
        String gameplayStatus = previewData != null ? previewData.getGameplayStatus() : "Unknown";
        int socialPoints = previewData != null ? previewData.getSocialPoints() : 0;
        int gameplayPoints = previewData != null ? previewData.getGameplayPoints() : 0;

        items.put(SOCIAL_SLOT, buildStatusItem(
                guiConfig.getSocialItem(),
                "standing_gui.social_status",
                socialStatus, socialPoints
        ));

        items.put(GAMEPLAY_SLOT, buildStatusItem(
                guiConfig.getGameplayItem(),
                "standing_gui.gameplay_status",
                gameplayStatus, gameplayPoints
        ));

        return items;
    }

    @Override
    protected Collection<Punishment> elements() {
        if (punishments.isEmpty())
            return Collections.singletonList(new Punishment());
        return punishments;
    }

    @Override
    protected CirrusItem map(Punishment punishment) {
        if (punishment.getId().isEmpty()) {
            return createEmptyPlaceholder("No punishments");
        }

        String id = punishment.getId();
        String date = MenuItems.formatDate(punishment.getIssued());
        String typeName = getTypeName(punishment);

        Long effectiveDuration = getEffectiveDuration(punishment);
        String duration;
        if (effectiveDuration == null || effectiveDuration <= 0)
            duration = "Permanent";
        else
            duration = MenuItems.formatDuration(effectiveDuration);

        String typeCategory;
        if (punishment.isBanType())
            typeCategory = "ban";
        else if (punishment.isMuteType())
            typeCategory = "mute";
        else
            typeCategory = punishment.getTypeCategory();

        String status = buildStatusString(punishment, effectiveDuration);
        String reason = getPlayerDescription(punishment);

        Map<String, String> vars = mapOf(
                "id", id,
                "date", date,
                "duration", duration,
                "type", typeCategory,
                "type_name", typeName,
                "status", status,
                "reason", reason
        );

        String title = localeManager.getMessage("standing_gui.punishment_item.title", vars);
        List<String> lore = localeManager.getMessageList("standing_gui.punishment_item.lore", vars);

        CirrusItemType itemType;
        if (punishment.isBanType())
            itemType = CirrusItemType.DIAMOND_SWORD;
        else
            itemType = CirrusItemType.PAPER;

        return CirrusItem.of(itemType, CirrusChatElement.ofLegacyText(MenuItems.translateColorCodes(title)),
                MenuItems.lore(lore));
    }

    @Override
    protected void handleClick(Click click, Punishment punishment) {
    }

    private CirrusItem buildStatusItem(String itemId, String localeBasePath, String status, int points) {
        String displayStatus = localeManager.getMessage("standing_gui.status_display." + status);
        if (displayStatus.startsWith("§cMissing")) {
            displayStatus = status;
        }

        Map<String, String> placeholders = mapOf(
                "status", displayStatus,
                "points", String.valueOf(points)
        );

        String title = localeManager.getMessage(localeBasePath + ".title", placeholders);

        List<String> desc = localeManager.getMessageList(localeBasePath + ".description." + status, placeholders);
        if (desc.size() == 1 && desc.get(0).contains("Missing locale list")) {
            String capitalized = status.isEmpty() ? status : status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
            desc = localeManager.getMessageList(localeBasePath + ".description." + capitalized, placeholders);
            if (desc.size() == 1 && desc.get(0).contains("Missing locale list")) {
                desc = listOf();
            }
        }

        CirrusItemType type = CirrusItemType.of(itemId);
        return CirrusItem.of(type, CirrusChatElement.ofLegacyText(MenuItems.translateColorCodes(title)),
                MenuItems.lore(desc));
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

    private String getPlayerDescription(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        if (typeData != null && typeData.getPlayerDescription() != null && !typeData.getPlayerDescription().isEmpty()) {
            return typeData.getPlayerDescription();
        }
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
        if (punishment.getStarted() == null) return false;
        if (effectiveDuration == null || effectiveDuration <= 0) return true;
        long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
        return System.currentTimeMillis() < expiryTime;
    }
}
