package gg.modl.minecraft.core.util;

import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class YamlMergeUtil {
    private static final Yaml yaml = new Yaml();

    private YamlMergeUtil() {}

    @SuppressWarnings("unchecked")
    public static void mergeWithDefaults(String jarResourcePath, Path externalFile, Logger logger) {
        try {
            if (!Files.exists(externalFile)) {
                try (InputStream jarStream = YamlMergeUtil.class.getResourceAsStream(jarResourcePath)) {
                    if (jarStream == null) {
                        logger.warning("JAR resource not found: " + jarResourcePath);
                        return;
                    }
                    Files.createDirectories(externalFile.getParent());
                    Files.copy(jarStream, externalFile);
                }
                return;
            }

            String jarText;
            try (InputStream jarStream = YamlMergeUtil.class.getResourceAsStream(jarResourcePath)) {
                if (jarStream == null) {
                    logger.warning("JAR resource not found: " + jarResourcePath);
                    return;
                }
                jarText = readStream(jarStream);
            }
            jarText = jarText.replace("\r\n", "\n").replace("\r", "\n");
            Map<Object, Object> defaults;
            try {
                defaults = yaml.load(jarText);
            } catch (Exception e) {
                logger.warning("Failed to parse JAR default " + jarResourcePath + ", skipping merge");
                return;
            }
            if (defaults == null) return;

            String userText = Files.readString(externalFile, StandardCharsets.UTF_8);
            userText = userText.replace("\r\n", "\n").replace("\r", "\n");

            Map<Object, Object> userValues;
            try {
                userValues = yaml.load(userText);
            } catch (Exception e) {
                logger.warning("Failed to parse " + externalFile.getFileName() + ", skipping merge: " + e.getMessage());
                return;
            }
            if (userValues == null) userValues = new LinkedHashMap<>();

            List<String> missingPaths = new ArrayList<>();
            collectMissingPaths(defaults, userValues, "", missingPaths);
            if (missingPaths.isEmpty()) return;

            List<String> jarLines = Arrays.asList(jarText.split("\n", -1));
            List<String> userLines = new ArrayList<>(Arrays.asList(userText.split("\n", -1)));

            int inserted = 0;
            for (String path : missingPaths) {
                String rawSection = extractSectionByPath(jarLines, path);
                if (rawSection == null) continue;
                int insertAt = findInsertionPoint(userLines, path);
                if (insertAt < 0) continue;

                List<String> newLines = new ArrayList<>(Arrays.asList(rawSection.split("\n", -1)));
                while (!newLines.isEmpty() && newLines.get(newLines.size() - 1).isEmpty()) {
                    newLines.remove(newLines.size() - 1);
                }

                userLines.addAll(insertAt, newLines);
                inserted++;
            }

            if (inserted > 0) {
                Files.writeString(externalFile, String.join("\n", userLines), StandardCharsets.UTF_8);
                logger.info("Merged " + inserted + " new section(s) into " + externalFile.getFileName());
            }

        } catch (IOException e) {
            logger.warning("Failed to merge defaults for " + externalFile.getFileName() + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectMissingPaths(Map<Object, Object> defaults, Map<Object, Object> userValues,
                                            String parentPath, List<String> missing) {
        for (Map.Entry<Object, Object> entry : defaults.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;

            if (!userValues.containsKey(entry.getKey())) missing.add(fullPath);
            else if (entry.getValue() instanceof Map && userValues.get(entry.getKey()) instanceof Map) {
                collectMissingPaths(
                        (Map<Object, Object>) entry.getValue(),
                        (Map<Object, Object>) userValues.get(entry.getKey()),
                        fullPath, missing
                );
            }
        }
    }

    private static String extractSectionByPath(List<String> lines, String path) {
        String[] parts = path.split("\\.");
        int searchFrom = 0;
        int expectedIndent = 0;

        for (int p = 0; p < parts.length; p++) {
            int keyLine = findKeyLine(lines, searchFrom, expectedIndent, parts[p]);
            if (keyLine < 0) return null;

            if (p == parts.length - 1) return extractSection(lines, keyLine, expectedIndent);

            int childIndent = detectChildIndent(lines, keyLine, expectedIndent);
            if (childIndent < 0) return null;
            searchFrom = keyLine + 1;
            expectedIndent = childIndent;
        }
        return null;
    }

    private static int findInsertionPoint(List<String> lines, String path) {
        String[] parts = path.split("\\.");

        if (parts.length == 1) {
            int end = lines.size();
            while (end > 0 && lines.get(end - 1).trim().isEmpty()) end--;
            return end;
        }

        int searchFrom = 0;
        int expectedIndent = 0;

        for (int p = 0; p < parts.length - 1; p++) {
            int keyLine = findKeyLine(lines, searchFrom, expectedIndent, parts[p]);
            if (keyLine < 0) return -1;

            int childIndent = detectChildIndent(lines, keyLine, expectedIndent);
            if (childIndent < 0) {
                return keyLine + 1;
            }

            if (p < parts.length - 2) {
                searchFrom = keyLine + 1;
                expectedIndent = childIndent;
            } else {
                return findSectionEnd(lines, keyLine, expectedIndent);
            }
        }
        return -1;
    }

    private static int findSectionEnd(List<String> lines, int sectionStart, int sectionIndent) {
        int lastContentLine = sectionStart;
        for (int i = sectionStart + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (getIndent(line) <= sectionIndent) break;
            lastContentLine = i;
        }
        return lastContentLine + 1;
    }

    private static int findKeyLine(List<String> lines, int from, int expectedIndent, String key) {
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            int indent = getIndent(line);
            if (indent < expectedIndent) return -1;
            if (indent > expectedIndent) continue;

            if (isKeyMatch(trimmed, key)) return i;
        }
        return -1;
    }

    private static boolean isKeyMatch(String trimmedLine, String key) {
        return trimmedLine.equals(key + ":")
                || trimmedLine.startsWith(key + ": ")
                || trimmedLine.equals("\"" + key + "\":")
                || trimmedLine.startsWith("\"" + key + "\": ")
                || trimmedLine.equals("'" + key + "':")
                || trimmedLine.startsWith("'" + key + "': ");
    }

    private static int detectChildIndent(List<String> lines, int parentLine, int parentIndent) {
        for (int i = parentLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int indent = getIndent(line);
            if (indent <= parentIndent) return -1;
            return indent;
        }
        return -1;
    }

    private static String extractSection(List<String> lines, int keyLine, int keyIndent) {
        int endLine = keyLine + 1;
        while (endLine < lines.size()) {
            String line = lines.get(endLine);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                endLine++;
                continue;
            }
            if (getIndent(line) <= keyIndent) break;
            endLine++;
        }
        while (endLine > keyLine + 1 && lines.get(endLine - 1).trim().isEmpty()) {
            endLine--;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = keyLine; i < endLine; i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static int getIndent(String line) {
        int indent = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') indent++;
            else break;
        }
        return indent;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMerge(Map<String, Object> defaults, Map<String, Object> userValues) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults);

        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String key = entry.getKey();
            Object userValue = entry.getValue();
            Object defaultValue = merged.get(key);

            if (defaultValue instanceof Map && userValue instanceof Map) {
                merged.put(key, deepMerge((Map<String, Object>) defaultValue, (Map<String, Object>) userValue));
            } else {
                merged.put(key, userValue);
            }
        }

        return merged;
    }

    private static String readStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }
}
