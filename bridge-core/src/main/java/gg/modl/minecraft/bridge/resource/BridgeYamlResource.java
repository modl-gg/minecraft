package gg.modl.minecraft.bridge.resource;

import gg.modl.minecraft.bridge.BridgePluginContext;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlMergeUtil;
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

    public static void ensureDefaultFile(BridgePluginContext context, String resourcePath, PluginLogger logger) {
        Path externalFile = context.getDataFolder().resolve(resourcePath);
        if (!Files.exists(externalFile)) {
            context.saveDefaultResource(resourcePath);
        }

        YamlMergeUtil.mergeWithDefaults("/" + resourcePath, externalFile, logger);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadMap(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(input);
            if (loaded == null) {
                return Collections.emptyMap();
            }
            return (Map<String, Object>) loaded;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadResourceMap(Class<?> owner, String resourcePath) throws IOException {
        try (InputStream input = owner.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new FileNotFoundException(resourcePath);
            }

            Object loaded = new Yaml().load(input);
            if (loaded == null) {
                return Collections.emptyMap();
            }
            return (Map<String, Object>) loaded;
        }
    }
}
