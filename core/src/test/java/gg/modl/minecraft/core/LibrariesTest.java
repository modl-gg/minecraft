package gg.modl.minecraft.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

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

    private static void assertReverseRelocations(String[][] relocations) {
        org.junit.jupiter.api.Assertions.assertEquals("gg{}modl{}libs{}packetevents{}api", relocations[0][0]);
        org.junit.jupiter.api.Assertions.assertEquals("com{}github{}retrooper{}packetevents", relocations[0][1]);
        org.junit.jupiter.api.Assertions.assertEquals("gg{}modl{}libs{}packetevents{}impl", relocations[1][0]);
        org.junit.jupiter.api.Assertions.assertEquals("io{}github{}retrooper{}packetevents", relocations[1][1]);
    }
}
