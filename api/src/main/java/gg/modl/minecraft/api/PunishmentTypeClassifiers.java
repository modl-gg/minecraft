package gg.modl.minecraft.api;

public final class PunishmentTypeClassifiers {

    private static volatile PunishmentTypeClassifier active = new RegistryPunishmentTypeClassifier();

    private PunishmentTypeClassifiers() {
    }

    public static void install(PunishmentTypeClassifier classifier) {
        active = classifier;
    }

    public static PunishmentTypeClassifier active() {
        return active;
    }
}
