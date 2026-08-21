package gg.modl.minecraft.core.config.yaml;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class YamlDocument {
    private static final String WINDOWS_LINE_ENDING = "\r\n";
    private static final String UNIX_LINE_ENDING = "\n";

    private final List<String> lines;
    private final String lineEnding;
    private final List<YamlEntry> roots = new ArrayList<>();
    private final List<String> duplicatedPaths = new ArrayList<>();

    private YamlDocument(List<String> lines, String lineEnding) {
        this.lines = lines;
        this.lineEnding = lineEnding;
    }

    public static YamlDocument parse(String text) {
        String lineEnding = text.contains(WINDOWS_LINE_ENDING) ? WINDOWS_LINE_ENDING : UNIX_LINE_ENDING;
        YamlDocument document = new YamlDocument(
                new ArrayList<>(Arrays.asList(normalize(text).split(UNIX_LINE_ENDING, -1))), lineEnding);
        document.build();
        return document;
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public List<YamlEntry> getRoots() {
        return Collections.unmodifiableList(roots);
    }

    public boolean hasDuplicateKeys() {
        return !duplicatedPaths.isEmpty();
    }

    public List<String> duplicatedPaths() {
        return Collections.unmodifiableList(duplicatedPaths);
    }

    public YamlEntry find(String path) {
        if (path == null || path.isEmpty()) return null;
        List<YamlEntry> level = roots;
        YamlEntry found = null;
        for (String part : path.split("\\.", -1)) {
            found = null;
            for (YamlEntry candidate : level) {
                if (candidate.getKey().equals(part)) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) return null;
            level = found.getChildren();
        }
        return found;
    }

    public int childIndentOf(YamlEntry parent) {
        int from = parent == null ? 0 : parent.getKeyLine() + 1;
        int limit = parent == null ? lines.size() : parent.getSectionEnd();
        int parentIndent = parent == null ? -1 : parent.getIndent();
        for (int i = from; i < limit; i++) {
            String line = lines.get(i);
            if (isBlank(line) || isComment(line)) continue;
            int indent = indentOf(line);
            if (indent <= parentIndent) break;
            return indent;
        }
        return parentIndent + 2;
    }

    public List<String> extractBlock(YamlEntry entry) {
        return new ArrayList<>(lines.subList(entry.getBlockStart(), entry.getSectionEnd()));
    }

    public String render() {
        return String.join(lineEnding, lines);
    }

    public void insert(int at, List<String> block) {
        lines.addAll(at, block);
    }

    private void build() {
        Deque<YamlEntry> open = new ArrayDeque<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlank(line) || isComment(line)) continue;

            int indent = indentOf(line);
            String key = keyOf(line);
            if (key == null) continue;

            int blockStart = commentStart(i);
            while (!open.isEmpty() && open.peek().getIndent() >= indent) {
                close(open.pop(), blockStart);
            }

            YamlEntry parent = open.peek();
            String path = parent == null ? key : parent.getPath() + "." + key;
            YamlEntry entry = new YamlEntry(key, path, indent, blockStart, i);
            List<YamlEntry> siblings = parent == null ? roots : parent.getChildren();
            for (YamlEntry sibling : siblings) {
                if (sibling.getKey().equals(key)) {
                    duplicatedPaths.add(path);
                    break;
                }
            }
            if (parent == null) roots.add(entry);
            else parent.addChild(entry);
            open.push(entry);
        }
        while (!open.isEmpty()) {
            close(open.pop(), lines.size());
        }
    }

    private void close(YamlEntry entry, int boundary) {
        int end = boundary;
        while (end > entry.getKeyLine() + 1 && isBlank(lines.get(end - 1))) end--;
        entry.setSectionEnd(end);
    }

    private int commentStart(int keyLine) {
        int start = keyLine;
        while (start > 0 && isComment(lines.get(start - 1))) start--;
        return start;
    }

    private static String keyOf(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("-")) return null;

        char quote = trimmed.charAt(0);
        if (quote == '"' || quote == '\'') {
            int closing = trimmed.indexOf(quote, 1);
            if (closing < 0) return null;
            String rest = trimmed.substring(closing + 1).trim();
            if (!rest.startsWith(":")) return null;
            return trimmed.substring(1, closing);
        }

        int colon = indexOfSeparator(trimmed);
        if (colon <= 0) return null;
        return trimmed.substring(0, colon).trim();
    }

    private static int indexOfSeparator(String trimmed) {
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != ':') continue;
            if (i + 1 == trimmed.length() || trimmed.charAt(i + 1) == ' ') return i;
        }
        return -1;
    }

    public static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') indent++;
        return indent;
    }

    public static boolean isBlank(String line) {
        return line.trim().isEmpty();
    }

    public static boolean isComment(String line) {
        return line.trim().startsWith("#");
    }

    private static String normalize(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        return normalized.startsWith("﻿") ? normalized.substring(1) : normalized;
    }
}
