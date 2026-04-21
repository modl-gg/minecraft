package gg.modl.minecraft.spigot;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpigotPlatformTest {

    @Test
    void shouldDisableBrigadierReturnsFalseDuringNormalBootstrap() {
        SpigotPlatform platform = new SpigotPlatform(mockPlugin(), Logger.getLogger("test"), new File("."), "server-1", false);

        assertFalse(platform.shouldDisableBrigadier());
    }

    @Test
    void shouldDisableBrigadierReturnsTrueForLateBootstrap() {
        SpigotPlatform platform = new SpigotPlatform(mockPlugin(), Logger.getLogger("test"), new File("."), "server-1", true);

        assertTrue(platform.shouldDisableBrigadier());
    }

    private static JavaPlugin mockPlugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("modl");
        return plugin;
    }
}
