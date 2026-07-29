package gg.modl.minecraft.core.boot;

import com.alessiodp.libby.Library;
import gg.modl.minecraft.api.LibraryRecord;

public final class LibraryLoader {
    private LibraryLoader() {}

    public static Library toLibrary(LibraryRecord record) {
        Library.Builder builder = Library.builder()
                .groupId(record.getGroupId())
                .artifactId(record.getArtifactId())
                .version(record.getVersion());

        if (record.hasRelocations()) {
            for (String[] relocation : record.getRelocations()) {
                builder.relocate(relocation[0], relocation[1]);
            }
        }
        if (record.getUrl() != null) builder.url(record.getUrl());
        if (record.hasChecksum()) builder.checksumFromBase64(record.getChecksum());

        return builder.build();
    }
}
