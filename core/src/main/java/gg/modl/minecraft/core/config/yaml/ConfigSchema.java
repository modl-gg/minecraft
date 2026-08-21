package gg.modl.minecraft.core.config.yaml;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ConfigSchema {
    private static final String ROOT = "";

    private final Set<String> dynamicPaths;

    private ConfigSchema(Set<String> dynamicPaths) {
        this.dynamicPaths = dynamicPaths;
    }

    public static ConfigSchema fixed() {
        return new ConfigSchema(Collections.<String>emptySet());
    }

    public static ConfigSchema withDynamicSections(String... paths) {
        return new ConfigSchema(new HashSet<>(Arrays.asList(paths)));
    }

    public static ConfigSchema fullyDynamic() {
        return new ConfigSchema(Collections.singleton(ROOT));
    }

    public boolean isDynamic(String path) {
        return dynamicPaths.contains(path);
    }

    public boolean isRootDynamic() {
        return dynamicPaths.contains(ROOT);
    }
}
