package gg.modl.minecraft.api;

import lombok.Value;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegistryPunishmentTypeClassifier implements RebuildablePunishmentTypeClassifier {

    private volatile Map<Integer, PunishmentTypeInfo> registry = new ConcurrentHashMap<>();
    private Map<Integer, PunishmentTypeInfo> staging;
    private volatile boolean populated = false;

    @Override
    public synchronized void beginRebuild() {
        staging = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized void commitRebuild() {
        if (staging != null) {
            registry = staging;
            staging = null;
        }
        populated = true;
    }

    @Override
    public void register(int ordinal, boolean isBan, boolean isMute) {
        Map<Integer, PunishmentTypeInfo> target = (staging != null) ? staging : registry;
        target.put(ordinal, new PunishmentTypeInfo(isBan, isMute));
        if (staging == null) populated = true;
    }

    @Override
    public void registerAdministrativeTypes() {
        register(ORDINAL_KICK, false, false);
        register(ORDINAL_MUTE, false, true);
        register(ORDINAL_BAN, true, false);
        register(ORDINAL_SECURITY_BAN, true, false);
        register(ORDINAL_LINKED_BAN, true, false);
        register(ORDINAL_BLACKLIST, true, false);
    }

    @Override
    public boolean isPopulated() {
        return populated;
    }

    @Override
    public boolean isBan(int ordinal) {
        PunishmentTypeInfo info = registry.get(ordinal);
        if (info != null) return info.isBan();
        return ordinal >= ORDINAL_BAN && ordinal <= ORDINAL_BLACKLIST;
    }

    @Override
    public boolean isMute(int ordinal) {
        PunishmentTypeInfo info = registry.get(ordinal);
        if (info != null) return info.isMute();
        return ordinal == ORDINAL_MUTE;
    }

    @Override
    public boolean isKick(int ordinal) {
        return ordinal == ORDINAL_KICK;
    }

    @Value
    private static class PunishmentTypeInfo {
        boolean isBan;
        boolean isMute;
    }
}
