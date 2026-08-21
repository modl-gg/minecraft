package gg.modl.minecraft.bridge.resource;

import gg.modl.minecraft.bridge.BridgePluginContext;
import gg.modl.minecraft.core.config.yaml.ConfigUpdate;
import gg.modl.minecraft.core.config.yaml.ConfigUpdater;
import gg.modl.minecraft.core.config.yaml.ManagedConfig;
import gg.modl.minecraft.core.util.PluginLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public final class BridgeYamlResource {

    private BridgeYamlResource() {
    }

    public static void ensureDefaultFile(BridgePluginContext context, ManagedConfig config, PluginLogger logger) {
        Path externalFile = context.getDataFolder().resolve(config.getFileName());
        if (!Files.exists(externalFile)) {
            context.saveDefaultResource(config.getFileName());
        }

        ConfigUpdate update = ConfigUpdater.update(config, context.getDataFolder(), logger);
        if (!update.getAddedPaths().isEmpty()) {
            logger.info("[config] Added " + update.getAddedPaths().size() + " new option(s) to "
                    + config.getFileName() + ": " + String.join(", ", update.getAddedPaths()));
        }
    }

    public static Map<String, Object> loadMap(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return asStringKeyedMap(new Yaml().load(input));
        }
    }

    public static Map<String, Object> loadResourceMap(Class<?> owner, String resourcePath) throws IOException {
        try (InputStream input = owner.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new FileNotFoundException(resourcePath);
            }
            return asStringKeyedMap(new Yaml().load(input));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyedMap(Object loaded) {
        return loaded instanceof Map ? (Map<String, Object>) loaded : Collections.emptyMap();
    }
}
