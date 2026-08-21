package gg.modl.minecraft.core.config.yaml;

import gg.modl.minecraft.core.util.PluginLogger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CoreConfigBootstrap {
    private static final String LOCALE_DIRECTORY = "locale";
    private static final String YAML_SUFFIX = ".yml";

    private CoreConfigBootstrap() {
    }

    public static void updateBootConfig(Path dataFolder, PluginLogger logger) {
        apply(CoreManagedConfigs.BOOT, dataFolder, logger);
    }

    public static void updateRuntimeConfigs(Path dataFolder, PluginLogger logger) {
        apply(CoreManagedConfigs.CONFIG, dataFolder, logger);
        for (String localeCode : installedLocaleCodes(dataFolder)) {
            apply(CoreManagedConfigs.locale(localeCode), dataFolder, logger);
        }
    }

    private static void apply(ManagedConfig config, Path dataFolder, PluginLogger logger) {
        ConfigUpdate update = ConfigUpdater.update(config, dataFolder, logger);
        if (!update.getAddedPaths().isEmpty()) {
            logger.info("[config] Added " + update.getAddedPaths().size() + " new option(s) to "
                    + config.getFileName() + ": " + String.join(", ", update.getAddedPaths()));
        }
    }

    private static boolean hasPackagedDefault(String localeCode) {
        return CoreConfigBootstrap.class.getResource(CoreManagedConfigs.locale(localeCode).getResourcePath()) != null;
    }

    private static List<String> installedLocaleCodes(Path dataFolder) {
        Path localeFolder = dataFolder.resolve(LOCALE_DIRECTORY);
        List<String> codes = new ArrayList<>();
        if (!Files.isDirectory(localeFolder)) {
            codes.add("en_US");
            return codes;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(localeFolder, "*" + YAML_SUFFIX)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                String code = name.substring(0, name.length() - YAML_SUFFIX.length());
                if (hasPackagedDefault(code)) codes.add(code);
            }
        } catch (IOException e) {
            codes.clear();
        }
        if (codes.isEmpty()) codes.add("en_US");
        return codes;
    }
}
