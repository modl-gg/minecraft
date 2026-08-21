package gg.modl.minecraft.core.packet;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketInterceptionPolicyTest {
    private static ProxyPluginRegistry registryOf(String... pluginIds) {
        Set<String> present = new HashSet<>(Arrays.asList(pluginIds));
        return present::contains;
    }

    @Test
    void autoEnablesOnAProxyWithoutPacketRewritingPlugins() {
        PacketInterceptionDecision decision =
                PacketInterceptionPolicy.decide(PacketInterceptionMode.AUTO, registryOf("luckperms"));

        assertTrue(decision.isEnabled());
    }

    @Test
    void autoDisablesWhenLimboApiIsInstalled() {
        PacketInterceptionDecision decision =
                PacketInterceptionPolicy.decide(PacketInterceptionMode.AUTO, registryOf("limboapi"));

        assertFalse(decision.isEnabled());
        assertEquals(1, decision.getConflicts().size());
        assertEquals("limboapi", decision.getConflicts().get(0).getPluginId());
        assertTrue(decision.getReason().contains(PacketInterceptionPolicy.CONFIG_KEY));
    }

    @Test
    void autoNamesEveryDetectedConflict() {
        PacketInterceptionDecision decision = PacketInterceptionPolicy.decide(
                PacketInterceptionMode.AUTO, registryOf("limboapi", "eaglerxserver"));

        assertFalse(decision.isEnabled());
        assertEquals(2, decision.getConflicts().size());
    }

    @Test
    void enabledOverridesADetectedConflict() {
        PacketInterceptionDecision decision =
                PacketInterceptionPolicy.decide(PacketInterceptionMode.ENABLED, registryOf("limboapi"));

        assertTrue(decision.isEnabled());
        assertEquals(1, decision.getConflicts().size());
    }

    @Test
    void disabledWinsEvenWithoutConflicts() {
        PacketInterceptionDecision decision = PacketInterceptionPolicy.decide(
                PacketInterceptionMode.DISABLED, registryOf());

        assertFalse(decision.isEnabled());
        assertTrue(decision.getConflicts().isEmpty());
    }

    @Test
    void unreadableConfigValuesFallBackToAuto() {
        assertEquals(PacketInterceptionMode.AUTO, PacketInterceptionMode.fromConfig(null));
        assertEquals(PacketInterceptionMode.AUTO, PacketInterceptionMode.fromConfig(Collections.emptyList()));
        assertEquals(PacketInterceptionMode.AUTO, PacketInterceptionMode.fromConfig("nonsense"));
        assertEquals(PacketInterceptionMode.ENABLED, PacketInterceptionMode.fromConfig(" Enabled "));
        assertEquals(PacketInterceptionMode.DISABLED, PacketInterceptionMode.fromConfig("DISABLED"));
    }
}
