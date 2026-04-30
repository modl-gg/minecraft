package gg.modl.minecraft.core.boot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BootConfigTest {

    @Test
    void acceptsBridgeAsBridgeOnlyAlias() {
        assertEquals(BootConfig.Mode.BRIDGE_ONLY, BootConfig.Mode.fromString("bridge"));
    }
}
