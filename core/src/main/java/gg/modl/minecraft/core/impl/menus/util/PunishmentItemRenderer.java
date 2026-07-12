package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.core.PluginServices;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class PunishmentItemRenderer {
    private static final String STATUS_KEY_PREFIX = "menus.history_item";

    private PunishmentItemRenderer() {}

    public static CirrusItem render(Punishment punishment, boolean isKick, boolean isBan, boolean isMute,
                                    String typeName, String loreKeyPrefix, Map<String, String> extraVars) {
        LocaleManager locale = PluginServices.locale();

        Long effectiveDuration = effectiveDuration(punishment);
        boolean isActive = !isKick && isEffectivelyActive(punishment, effectiveDuration);

        String initialDuration = "";
        if (!isKick) {
            Long duration = punishment.getDuration();
            if (duration == null || duration <= 0) {
                initialDuration = "Permanent";
            } else {
                initialDuration = MenuItems.formatDuration(duration);
            }
        }

        String spaceBanMuteOrKick = "";
        if (isKick) {
            spaceBanMuteOrKick = "Kick";
        } else if (isBan) {
            spaceBanMuteOrKick = " Ban";
        } else if (isMute) {
            spaceBanMuteOrKick = " Mute";
        }

        String statusLine = buildStatusLine(locale, punishment, isKick, isActive, effectiveDuration);

        StringBuilder notesBuilder = new StringBuilder();
        List<Note> notes = punishment.getNotes();
        if (!notes.isEmpty()) {
            String noteFormat = locale.getMessage(STATUS_KEY_PREFIX + ".note_format");
            for (int i = 0; i < notes.size(); i++) {
                Note note = notes.get(i);
                String noteDate = MenuItems.formatDate(note.getDate());
                String noteIssuer = note.getIssuerName();
                String noteText = note.getText();
                String formattedNote = noteFormat
                        .replace("{note_date}", noteDate)
                        .replace("{note_issuer}", noteIssuer)
                        .replace("{note}", noteText);
                if (i > 0) notesBuilder.append("\n");
                notesBuilder.append(formattedNote);
            }
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("punishment_id", punishment.getId());
        vars.put("punishment_type", typeName);
        vars.put("initial_duration_if_not_kick", initialDuration);
        vars.put("space_ban_mute_or_kick", spaceBanMuteOrKick);
        vars.put("status_line", statusLine);
        vars.put("notes", notesBuilder.toString());
        vars.put("reason", punishment.getReason() != null ? punishment.getReason() : "No reason");
        vars.put("issuer", punishment.getIssuerName());
        vars.put("issued_date", MenuItems.formatDate(punishment.getIssued()));
        Object issuedServerObj = punishment.getDataMap().get("issuedServer");
        vars.put("issued_server", issuedServerObj instanceof String ? (String) issuedServerObj : "");
        if (extraVars != null) vars.putAll(extraVars);

        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList(loreKeyPrefix + ".lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (processed.contains("\n")) {
                lore.addAll(Arrays.asList(processed.split("\n")));
            } else if (!processed.isEmpty()) {
                lore.add(processed);
            }
        }

        String titleKey = isActive ? loreKeyPrefix + ".title_active" : loreKeyPrefix + ".title_inactive";
        String title = locale.getMessage(titleKey, vars);
        CirrusItemType itemType = itemType(punishment);

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private static String buildStatusLine(LocaleManager locale, Punishment punishment, boolean isKick,
                                          boolean isActive, Long effectiveDuration) {
        Date pardonDate = isKick ? null : punishment.getPardonDate();
        if (isKick) {
            return "";
        }
        if (pardonDate != null) {
            long pardonedAgo = System.currentTimeMillis() - pardonDate.getTime();
            String pardonedFormatted = MenuItems.formatDuration(pardonedAgo > 0 ? pardonedAgo : 0);
            return locale.getMessage(STATUS_KEY_PREFIX + ".status_pardoned", mapOf("pardoned", pardonedFormatted));
        }
        if (punishment.getStarted() == null) {
            return locale.getMessage(STATUS_KEY_PREFIX + ".status_unstarted");
        }
        if (isActive) {
            if (effectiveDuration == null || effectiveDuration <= 0) {
                return locale.getMessage(STATUS_KEY_PREFIX + ".status_permanent");
            }
            long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
            long remaining = expiryTime - System.currentTimeMillis();
            String expiryFormatted = MenuItems.formatDuration(remaining > 0 ? remaining : 0);
            return locale.getMessage(STATUS_KEY_PREFIX + ".status_active", mapOf("expiry", expiryFormatted));
        }
        if (effectiveDuration != null && effectiveDuration > 0 && punishment.getStarted() != null) {
            long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
            long expiredAgo = System.currentTimeMillis() - expiryTime;
            String expiredFormatted = MenuItems.formatDuration(expiredAgo > 0 ? expiredAgo : 0);
            return locale.getMessage(STATUS_KEY_PREFIX + ".status_inactive", mapOf("expired", expiredFormatted));
        }
        return locale.getMessage(STATUS_KEY_PREFIX + ".status_inactive", mapOf("expired", "N/A"));
    }

    public static Long effectiveDuration(Punishment punishment) {
        List<Modification> modifications = punishment.getModifications();
        if (modifications.isEmpty()) return punishment.getDuration();

        Long effectiveDuration = punishment.getDuration();
        for (Modification mod : modifications) {
            if (mod.getType() == Modification.Type.MANUAL_DURATION_CHANGE) {
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

    public static boolean isEffectivelyActive(Punishment punishment, Long effectiveDuration) {
        if (punishment.getPardonDate() != null) return false;
        if (!punishment.isActive()) return false;
        if (punishment.getStarted() == null) return false;
        if (effectiveDuration == null || effectiveDuration <= 0) return true;
        long expiryTime = punishment.getStarted().getTime() + effectiveDuration;
        return System.currentTimeMillis() < expiryTime;
    }

    public static CirrusItemType itemType(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();

        Cache cache = PluginServices.cache();
        if (cache != null) {
            Map<Integer, String> items = cache.getPunishmentTypeItems();
            if (items != null) {
                String itemId = items.get(ordinal);
                if (itemId != null) return CirrusItemType.of(itemId);
            }
        }

        if (punishment.isBanType()) return CirrusItemType.BARRIER;
        if (punishment.isMuteType()) return CirrusItemType.PAPER;
        if (punishment.isKickType()) return CirrusItemType.LEATHER_BOOTS;
        return CirrusItemType.PAPER;
    }
}
