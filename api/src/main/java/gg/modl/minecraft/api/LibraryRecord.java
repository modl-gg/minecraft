package gg.modl.minecraft.api;

/**
 * Represents a library to be loaded at runtime via libby.
 *
 * @param groupId       Maven group ID
 * @param artifactId    Maven artifact ID
 * @param version       Maven version
 * @param id            Unique identifier for the library
 * @param oldRelocation Package to relocate from (null if no relocation)
 * @param newRelocation Package to relocate to (null if no relocation)
 * @param url           Direct download URL (null to use standard Maven resolution)
 * @param checksum      SHA-256 checksum for integrity verification (null to skip verification)
 */
public record LibraryRecord(
        String groupId,
        String artifactId,
        String version,
        String id,
        String oldRelocation,
        String newRelocation,
        String url,
        String checksum
) {
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
