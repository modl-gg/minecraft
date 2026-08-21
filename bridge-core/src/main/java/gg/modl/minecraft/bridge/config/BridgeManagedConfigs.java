package gg.modl.minecraft.bridge.config;

import gg.modl.minecraft.core.config.yaml.ConfigSchema;
import gg.modl.minecraft.core.config.yaml.ManagedConfig;

public final class BridgeManagedConfigs {
    public static final ManagedConfig BRIDGE_CONFIG = ManagedConfig.of("bridge-config.yml",
            ConfigSchema.withDynamicSections("report-violation-threshold"));

    public static final ManagedConfig STAFF_MODE = ManagedConfig.of("staff_mode.yml",
            ConfigSchema.withDynamicSections("staff_hotbar", "target_hotbar"));

    private BridgeManagedConfigs() {
    }
}
