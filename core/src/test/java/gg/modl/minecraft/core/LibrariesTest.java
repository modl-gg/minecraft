package gg.modl.minecraft.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import gg.modl.minecraft.api.LibraryRecord;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibrariesTest {
    @Test
    void packetEventsLibrariesRemainCanonicalForRuntimeLoadedPlatforms() {
        assertFalse(Libraries.PACKETEVENTS_API.hasRelocations());
        assertFalse(Libraries.PACKETEVENTS_NETTY.hasRelocations());
        assertFalse(Libraries.PACKETEVENTS_SPIGOT.hasRelocations());
        assertFalse(Libraries.PACKETEVENTS_BUNGEE.hasRelocations());
        assertFalse(Libraries.PACKETEVENTS_VELOCITY.hasRelocations());
        assertFalse(Libraries.PACKETEVENTS_FABRIC_COMMON.hasRelocations());
        assertFalse(Libraries.PACKETEVENTS_FABRIC_INTERMEDIARY.hasRelocations());
        assertFalse(Libraries.PACKETEVENTS_FABRIC_OFFICIAL.hasRelocations());
    }

    @Test
    void legacyCirrusPlatformArtifactsAreReverseRelocatedToCanonicalPacketEvents() {
        assertReverseRelocations(Libraries.CIRRUS_SPIGOT.getRelocations());
        assertReverseRelocations(Libraries.CIRRUS_BUNGEECORD.getRelocations());
        assertReverseRelocations(Libraries.CIRRUS_VELOCITY.getRelocations());
        assertFalse(Libraries.CIRRUS_FABRIC.hasRelocations());
    }

    @Test
    void cirrusChecksumsMatchCurrentRuntimeExpectations() {
        assertEquals("UBSO7Eenxuj/Xs2tvxEgOOOZnhA2BGM9cQqaFXJXxdI=", Libraries.CIRRUS_SPIGOT.getChecksum());
        assertEquals("mi6v+Xa7F29OI4BOTVcuJSpz4Z+cI/m6BwmBKFmvg34=", Libraries.CIRRUS_BUNGEECORD.getChecksum());
        assertEquals("9wAG0uzxFitqPdb/UESODtJG8rezsF8QdTybrPxMoV8=", Libraries.CIRRUS_VELOCITY.getChecksum());
        assertEquals("LELadOgUGGv2iyKXM0u55N986FNLjmL6FwHqF/zegFs=", Libraries.CIRRUS_FABRIC.getChecksum());
    }

    @Test
    void runtimeLoadedLibrariesHaveChecksums() {
        runtimeLibraries().forEach(library -> assertTrue(library.hasChecksum(), library.getId()));
    }

    private static void assertReverseRelocations(String[][] relocations) {
        assertArrayEquals(
                new String[]{"gg{}modl{}libs{}packetevents{}api", "com{}github{}retrooper{}packetevents"},
                relocations[0]
        );
        assertArrayEquals(
                new String[]{"gg{}modl{}libs{}packetevents{}impl", "io{}github{}retrooper{}packetevents"},
                relocations[1]
        );
    }

    private static List<LibraryRecord> runtimeLibraries() {
        return Arrays.stream(Libraries.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> LibraryRecord.class.equals(field.getType()))
                .map(LibrariesTest::readLibraryRecord)
                .collect(Collectors.toList());
    }

    private static LibraryRecord readLibraryRecord(Field field) {
        try {
            return (LibraryRecord) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Unable to read library record " + field.getName(), e);
        }
    }
}
