package gg.modl.minecraft.core.config.yaml;

import gg.modl.minecraft.core.util.PluginLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigUpdater {
    private static final String BACKUP_SUFFIX = ".bak";

    private ConfigUpdater() {
    }

    public static ConfigUpdate update(ManagedConfig config, Path dataFolder, PluginLogger logger) {
        String resourcePath = config.getResourcePath();
        ConfigSchema schema = config.getSchema();
        Path target = dataFolder.resolve(config.getFileName());
        String defaultText = readResource(resourcePath);
        if (defaultText == null) {
            logger.warning("[config] Packaged default not found: " + resourcePath);
            return ConfigUpdate.unchanged();
        }

        try {
            if (!Files.exists(target)) {
                if (target.getParent() != null) Files.createDirectories(target.getParent());
                Files.write(target, defaultText.getBytes(StandardCharsets.UTF_8));
                return ConfigUpdate.created();
            }
            String userText = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            Map<String, Object> defaults = load(defaultText);
            Map<String, Object> current = load(userText);
            if (defaults == null) {
                logger.warning("[config] Packaged default is not valid YAML: " + resourcePath);
                return ConfigUpdate.unchanged();
            }
            if (current == null) {
                logger.warning("[config] Leaving " + target.getFileName() + " untouched, it is not valid YAML");
                return ConfigUpdate.unchanged();
            }

            List<String> missing = new ArrayList<>();
            collectMissing(defaults, current, "", schema, missing);
            if (missing.isEmpty()) return ConfigUpdate.unchanged();

            YamlDocument userDocument = YamlDocument.parse(userText);
            if (userDocument.hasDuplicateKeys()) {
                logger.warning("[config] Leaving " + target.getFileName()
                        + " untouched, it declares the same key more than once: "
                        + String.join(", ", userDocument.duplicatedPaths()));
                return ConfigUpdate.unchanged();
            }

            String updated = applyAll(userDocument, YamlDocument.parse(defaultText), missing);
            if (!isSafeReplacement(updated, defaults, current, schema)) {
                logger.warning("[config] Could not safely add " + String.join(", ", missing)
                        + " to " + target.getFileName() + "; leaving it untouched");
                return ConfigUpdate.unchanged();
            }

            backUpOnce(target);
            writeAtomically(target, updated);
            return ConfigUpdate.added(missing);
        } catch (IOException e) {
            logger.warning("[config] Failed to update " + target.getFileName(), e);
            return ConfigUpdate.unchanged();
        }
    }

    private static boolean isSafeReplacement(String updated, Map<String, Object> defaults,
                                            Map<String, Object> current, ConfigSchema schema) {
        Map<String, Object> rendered = load(updated);
        if (rendered == null) return false;

        List<String> stillMissing = new ArrayList<>();
        collectMissing(defaults, rendered, "", schema, stillMissing);
        return stillMissing.isEmpty() && retainsAll(current, rendered);
    }

    @SuppressWarnings("unchecked")
    private static boolean retainsAll(Map<String, Object> original, Map<String, Object> rendered) {
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            if (!rendered.containsKey(entry.getKey())) return false;
            Object before = entry.getValue();
            if (before == null) continue;

            Object after = rendered.get(entry.getKey());
            if (before instanceof Map && after instanceof Map) {
                if (!retainsAll(asStringKeyed((Map<Object, Object>) before),
                        asStringKeyed((Map<Object, Object>) after))) {
                    return false;
                }
            } else if (!before.equals(after)) {
                return false;
            }
        }
        return true;
    }

    private static void backUpOnce(Path target) throws IOException {
        Path backup = target.resolveSibling(target.getFileName() + BACKUP_SUFFIX);
        if (!Files.exists(backup)) Files.copy(target, backup);
    }

    private static String applyAll(YamlDocument document, YamlDocument defaults, List<String> missing) {
        YamlDocument current = document;
        for (String path : missing) {
            YamlEntry source = defaults.find(path);
            if (source == null) continue;
            List<String> block = reindent(defaults, current, defaults.extractBlock(source), path);
            int at = insertionPoint(current, defaults, path);
            if (at < 0) continue;
            current.insert(at, separated(current, block, at));
            current = YamlDocument.parse(current.render());
        }
        return current.render();
    }

    private static List<String> separated(YamlDocument document, List<String> block, int at) {
        List<String> spaced = new ArrayList<>(block);
        boolean topLevel = !block.isEmpty() && YamlDocument.indentOf(block.get(0)) == 0;
        if (topLevel && at > 0 && !YamlDocument.isBlank(document.getLines().get(at - 1))) {
            spaced.add(0, "");
        }
        if (topLevel && at < document.getLines().size()
                && !YamlDocument.isBlank(document.getLines().get(at))) {
            spaced.add("");
        }
        return spaced;
    }

    private static int insertionPoint(YamlDocument document, YamlDocument defaults, String path) {
        int lastDot = path.lastIndexOf('.');
        String parentPath = lastDot < 0 ? null : path.substring(0, lastDot);
        YamlEntry parent = parentPath == null ? null : document.find(parentPath);
        if (parentPath != null && parent == null) return -1;

        List<YamlEntry> defaultSiblings = parentPath == null
                ? defaults.getRoots()
                : childrenOf(defaults.find(parentPath));
        List<YamlEntry> currentSiblings = parent == null ? document.getRoots() : parent.getChildren();

        int position = indexOfKey(defaultSiblings, keyOf(path));
        for (int i = position - 1; i >= 0; i--) {
            YamlEntry existing = byKey(currentSiblings, defaultSiblings.get(i).getKey());
            if (existing != null) return existing.getSectionEnd();
        }
        for (int i = position + 1; i < defaultSiblings.size(); i++) {
            YamlEntry existing = byKey(currentSiblings, defaultSiblings.get(i).getKey());
            if (existing != null) return existing.getBlockStart();
        }
        if (parent != null) return parent.getSectionEnd();

        int end = document.getLines().size();
        while (end > 0 && YamlDocument.isBlank(document.getLines().get(end - 1))) end--;
        return end;
    }

    private static List<String> reindent(YamlDocument defaults, YamlDocument document,
                                         List<String> block, String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0) return block;

        String parentPath = path.substring(0, lastDot);
        int sourceIndent = defaults.childIndentOf(defaults.find(parentPath));
        int targetIndent = document.childIndentOf(document.find(parentPath));
        int shift = targetIndent - sourceIndent;
        if (shift == 0) return block;

        List<String> shifted = new ArrayList<>(block.size());
        for (String line : block) {
            if (YamlDocument.isBlank(line)) {
                shifted.add(line);
            } else if (shift > 0) {
                shifted.add(repeat(shift) + line);
            } else {
                int removable = Math.min(-shift, YamlDocument.indentOf(line));
                shifted.add(line.substring(removable));
            }
        }
        return shifted;
    }

    @SuppressWarnings("unchecked")
    private static void collectMissing(Map<String, Object> defaults, Map<String, Object> current,
                                       String parentPath, ConfigSchema schema, List<String> missing) {
        if (schema.isDynamic(parentPath)) return;

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            String path = parentPath.isEmpty() ? key : parentPath + "." + key;
            if (schema.isDynamic(path) && current.containsKey(key)) continue;

            if (!current.containsKey(key)) {
                missing.add(path);
                continue;
            }
            Object defaultValue = entry.getValue();
            if (!(defaultValue instanceof Map)) continue;

            Object currentValue = current.get(key);
            if (currentValue == null) {
                collectMissing(asStringKeyed((Map<Object, Object>) defaultValue),
                        Collections.<String, Object>emptyMap(), path, schema, missing);
            } else if (currentValue instanceof Map) {
                collectMissing(asStringKeyed((Map<Object, Object>) defaultValue),
                        asStringKeyed((Map<Object, Object>) currentValue), path, schema, missing);
            }
        }
    }

    private static Map<String, Object> asStringKeyed(Map<Object, Object> source) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String text) {
        try {
            Object loaded = new Yaml().load(text);
            if (loaded == null) return new LinkedHashMap<>();
            if (!(loaded instanceof Map)) return null;
            return asStringKeyed((Map<Object, Object>) loaded);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<YamlEntry> childrenOf(YamlEntry entry) {
        return entry == null ? new ArrayList<YamlEntry>() : entry.getChildren();
    }

    private static YamlEntry byKey(List<YamlEntry> entries, String key) {
        for (YamlEntry entry : entries) {
            if (entry.getKey().equals(key)) return entry;
        }
        return null;
    }

    private static int indexOfKey(List<YamlEntry> entries, String key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getKey().equals(key)) return i;
        }
        return entries.size();
    }

    private static String keyOf(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }

    private static String repeat(int spaces) {
        StringBuilder builder = new StringBuilder(spaces);
        for (int i = 0; i < spaces; i++) builder.append(' ');
        return builder.toString();
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readResource(String resourcePath) {
        try (InputStream stream = ConfigUpdater.class.getResourceAsStream(resourcePath)) {
            if (stream == null) return null;
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) builder.append(buffer, 0, read);
            }
            return builder.toString().replace("\r\n", "\n").replace("\r", "\n");
        } catch (IOException e) {
            return null;
        }
    }
}
