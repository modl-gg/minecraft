package gg.modl.minecraft.bridge.resource;

import gg.modl.minecraft.bridge.BridgePlayerProvider;
import gg.modl.minecraft.bridge.BridgePluginContext;
import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeYamlResourceTest {

    @TempDir
    Path tempDir;

    @Test
    void ensureDefaultFileCopiesMissingPackagedResource() throws IOException {
        BridgeYamlResource.ensureDefaultFile(new TestContext(tempDir), "staff_mode.yml", testLogger());

        Path copiedFile = tempDir.resolve("staff_mode.yml");
        assertTrue(Files.exists(copiedFile));
        assertEquals(resourceText("staff_mode.yml"), new String(Files.readAllBytes(copiedFile), StandardCharsets.UTF_8));
    }

    @Test
    void loadResourceMapLoadsPackagedResource() throws IOException {
        assertTrue(BridgeYamlResource.loadResourceMap(getClass(), "/bridge_locale/en_US.yml")
                .containsKey("staff_mode"));
    }

    private static String resourceText(String resourcePath) throws IOException {
        try (InputStream input = BridgeYamlResourceTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing resource " + resourcePath);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private PluginLogger testLogger() {
        return PluginLogger.fromJul(Logger.getLogger("test"));
    }

    private static class TestContext implements BridgePluginContext {
        private final Path dataFolder;

        private TestContext(Path dataFolder) {
            this.dataFolder = dataFolder;
        }

        @Override
        public BridgeScheduler getScheduler() {
            return null;
        }

        @Override
        public BridgePlayerProvider getPlayerProvider() {
            return null;
        }

        @Override
        public Path getDataFolder() {
            return dataFolder;
        }

        @Override
        public Logger getLogger() {
            return Logger.getLogger("test");
        }

        @Override
        public void saveDefaultResource(String resourcePath) {
            try {
                Path target = dataFolder.resolve(resourcePath);
                Files.createDirectories(target.getParent());
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (input != null) {
                        Files.copy(input, target);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String getMinecraftVersion() {
            return "test";
        }
    }
}
