package gg.modl.minecraft.api;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents a library to be loaded at runtime via libby.
 */
@Data @AllArgsConstructor
public class LibraryRecord {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String id;
    private final String oldRelocation;
    private final String newRelocation;
    private final String url;
    private final String checksum;

    public static LibraryRecord of(String groupId, String artifactId, String version, String id) {
        return new LibraryRecord(groupId, artifactId, version, id, null, null, null, null);
    }

    public static LibraryRecord of(String groupId, String artifactId, String version, String id, String checksum) {
        return new LibraryRecord(groupId, artifactId, version, id, null, null, null, checksum);
    }

    public static LibraryRecord ofUrl(String groupId, String artifactId, String version, String id, String url, String checksum) {
        return new LibraryRecord(groupId, artifactId, version, id, null, null, url, checksum);
    }

    public boolean hasRelocation() {
        return oldRelocation != null && newRelocation != null;
    }

    public boolean hasChecksum() {
        return checksum != null;
    }
}
