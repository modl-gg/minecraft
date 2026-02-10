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
 */
public record LibraryRecord(
        String groupId,
        String artifactId,
        String version,
        String id,
        String oldRelocation,
        String newRelocation,
        String url
) {
    /**
     * Creates a LibraryRecord without relocation.
     */
    public static LibraryRecord of(String groupId, String artifactId, String version, String id) {
        return new LibraryRecord(groupId, artifactId, version, id, null, null, null);
    }

    /**
     * Creates a LibraryRecord with relocation.
     */
    public static LibraryRecord ofRelocated(String groupId, String artifactId, String version, String id,
                                            String oldRelocation, String newRelocation) {
        return new LibraryRecord(groupId, artifactId, version, id, oldRelocation, newRelocation, null);
    }

    /**
     * Creates a LibraryRecord with a direct download URL (for SNAPSHOT or non-standard repos).
     */
    public static LibraryRecord ofUrl(String groupId, String artifactId, String version, String id, String url) {
        return new LibraryRecord(groupId, artifactId, version, id, null, null, url);
    }

    /**
     * Returns true if this library requires relocation.
     */
    public boolean hasRelocation() {
        return oldRelocation != null && newRelocation != null;
    }
}
