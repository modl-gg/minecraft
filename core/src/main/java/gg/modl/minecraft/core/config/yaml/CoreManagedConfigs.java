package gg.modl.minecraft.core.config.yaml;

import java.util.Arrays;
import java.util.List;

public final class CoreManagedConfigs {
    public static final ManagedConfig BOOT = ManagedConfig.of("boot.yml", ConfigSchema.fixed());
    public static final ManagedConfig CONFIG = ManagedConfig.of("config.yml", ConfigSchema.fixed());
    public static final ManagedConfig PUNISH_GUI = ManagedConfig.of("punish_gui.yml", ConfigSchema.fullyDynamic());
    public static final ManagedConfig REPORT_GUI = ManagedConfig.of("report_gui.yml", ConfigSchema.fullyDynamic());

    private CoreManagedConfigs() {
    }

    public static ManagedConfig locale(String localeCode) {
        return ManagedConfig.of("locale/" + localeCode + ".yml", ConfigSchema.fixed());
    }

    public static List<ManagedConfig> guiConfigs() {
        return Arrays.asList(PUNISH_GUI, REPORT_GUI);
    }
}
