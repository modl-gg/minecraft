package gg.modl.minecraft.bridge.locale;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BridgeLocaleManagerTest {

    @Test
    void loadsPackagedMessagesAndAppliesPlaceholders() {
        BridgeLocaleManager manager = new BridgeLocaleManager(Logger.getLogger("test"));

        assertEquals("§aYou are now targeting §fAlice§a.",
                manager.getMessage("staff_mode.target.now_targeting", Collections.singletonMap("player", "Alice")));
    }

    @Test
    void returnsKeyWhenMessageIsMissing() {
        BridgeLocaleManager manager = new BridgeLocaleManager(Logger.getLogger("test"));

        assertEquals("missing.message", manager.getMessage("missing.message", Collections.emptyMap()));
    }
}
