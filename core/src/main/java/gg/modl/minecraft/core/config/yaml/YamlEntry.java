package gg.modl.minecraft.core.config.yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class YamlEntry {
    private final String key;
    private final String path;
    private final int indent;
    private final int blockStart;
    private final int keyLine;
    private final List<YamlEntry> children = new ArrayList<>();

    private int sectionEnd;

    YamlEntry(String key, String path, int indent, int blockStart, int keyLine) {
        this.key = key;
        this.path = path;
        this.indent = indent;
        this.blockStart = blockStart;
        this.keyLine = keyLine;
        this.sectionEnd = keyLine + 1;
    }

    public String getKey() {
        return key;
    }

    public String getPath() {
        return path;
    }

    public int getIndent() {
        return indent;
    }

    public int getBlockStart() {
        return blockStart;
    }

    public int getKeyLine() {
        return keyLine;
    }

    public int getSectionEnd() {
        return sectionEnd;
    }

    public List<YamlEntry> getChildren() {
        return Collections.unmodifiableList(children);
    }

    void addChild(YamlEntry child) {
        children.add(child);
    }

    void setSectionEnd(int sectionEnd) {
        this.sectionEnd = sectionEnd;
    }

    YamlEntry childByKey(String childKey) {
        for (YamlEntry child : children) {
            if (child.key.equals(childKey)) return child;
        }
        return null;
    }
}
