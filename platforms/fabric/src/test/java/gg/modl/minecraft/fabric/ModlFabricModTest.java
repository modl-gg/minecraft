package gg.modl.minecraft.fabric;

import gg.modl.minecraft.core.Libraries;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModlFabricModTest {
    @Test
    void routes1212Through1216To1214Implementation() {
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_4.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.2"));
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_4.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.5"));
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_4.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.6"));
    }

    @Test
    void routes1217Through12110To1218Implementation() {
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_8.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.7"));
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_8.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.8"));
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_8.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.10"));
    }

    @Test
    void routes12111AndNewerTo12111Implementation() {
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_11.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.11"));
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_11.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.12"));
    }

    @Test
    void preservesOlderRoutingBoundaries() {
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_1.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.1"));
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_4.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.4"));
        assertEquals(
                "gg.modl.minecraft.fabric.v26.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("26.1"));
        assertEquals(
                "gg.modl.minecraft.fabric.v26.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("26.1.2"));
    }

    @Test
    void rootFabricMetadataRequiresRuntimeDepsWithoutNestedImplMods() throws IOException {
        try (InputStream stream = ModlFabricModTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(stream, "fabric.mod.json should be present on the test classpath");

            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"packetevents-modl\": \"*\""));
            assertFalse(json.contains("\"packetevents\": \"*\""));
            assertTrue(json.contains("\"fabric-api\": \"*\""));
            assertFalse(json.contains("packetevents-modl.jar"));
            assertFalse(json.contains("modl-fabric-121.jar"));
            assertFalse(json.contains("modl-fabric-1214.jar"));
            assertFalse(json.contains("modl-fabric-1218.jar"));
            assertFalse(json.contains("modl-fabric-12111.jar"));
            assertFalse(json.contains("modl-fabric-26.jar"));
        }
    }

    @Test
    void cirrusFabricLibraryUsesCanonicalArtifactWithoutRelocation() {
        assertEquals("4.2.4", Libraries.CIRRUS_FABRIC.getVersion());
        assertFalse(Libraries.CIRRUS_FABRIC.hasRelocations());
        assertTrue(Libraries.CIRRUS_FABRIC.hasChecksum());
    }
}
