package gg.modl.minecraft.bridge.locale;

import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BridgeLocaleManagerTest {

    @Test
    void loadsPackagedMessagesAndAppliesPlaceholders() {
        BridgeLocaleManager manager = new BridgeLocaleManager(PluginLogger.fromJul(Logger.getLogger("test")));

        assertEquals("§aYou are now targeting §fAlice§a.",
                manager.getMessage("staff_mode.target.now_targeting", Collections.singletonMap("player", "Alice")));
    }

    @Test
    void returnsKeyWhenMessageIsMissing() {
        BridgeLocaleManager manager = new BridgeLocaleManager(PluginLogger.fromJul(Logger.getLogger("test")));

        assertEquals("missing.message", manager.getMessage("missing.message", Collections.emptyMap()));
    }
}
