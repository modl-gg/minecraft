package gg.modl.minecraft.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class LibraryRecord {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String id;
    private final String[][] relocations;
    private final String url;
    private final String checksum;

    public static LibraryRecord of(String groupId, String artifactId, String version, String id) {
        return new LibraryRecord(groupId, artifactId, version, id, null, null, null);
    }

    public static LibraryRecord of(String groupId, String artifactId, String version, String id, String checksum) {
        return new LibraryRecord(groupId, artifactId, version, id, null, null, checksum);
    }

    public static LibraryRecord of(String groupId, String artifactId, String version, String id, String checksum, String[][] relocations) {
        return new LibraryRecord(groupId, artifactId, version, id, relocations, null, checksum);
    }

    public static LibraryRecord ofUrl(String groupId, String artifactId, String version, String id, String url, String checksum) {
        return new LibraryRecord(groupId, artifactId, version, id, null, url, checksum);
    }

    public static LibraryRecord ofUrl(String groupId, String artifactId, String version, String id, String url, String checksum, String[][] relocations) {
        return new LibraryRecord(groupId, artifactId, version, id, relocations, url, checksum);
    }

    public boolean hasRelocations() {
        return relocations != null && relocations.length > 0;
    }

    public boolean hasChecksum() {
        return checksum != null;
    }
}
