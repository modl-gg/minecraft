package gg.modl.minecraft.api;

public interface RebuildablePunishmentTypeClassifier extends PunishmentTypeClassifier {
    void beginRebuild();

    void register(int ordinal, boolean isBan, boolean isMute);

    void registerAdministrativeTypes();

    void commitRebuild();
}
