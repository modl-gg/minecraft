package gg.modl.minecraft.fabric;

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
    void routesPost1214VersionsTo12111Implementation() {
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_11.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.5"));
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_11.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.10"));
        assertEquals(
                "gg.modl.minecraft.fabric.v1_21_11.ModlFabricModImpl",
                ModlFabricMod.selectImplementationClass("1.21.11"));
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
    }

    @Test
    void rootFabricMetadataRequiresPacketEventsWithoutBundlingItsJar() throws IOException {
        try (InputStream stream = ModlFabricModTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(stream, "fabric.mod.json should be present on the test classpath");

            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"packetevents\": \"*\""));
            assertFalse(json.contains("packetevents-fabric.jar"));
        }
    }
}
