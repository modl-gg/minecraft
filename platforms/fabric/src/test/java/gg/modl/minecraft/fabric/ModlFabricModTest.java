package gg.modl.minecraft.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
