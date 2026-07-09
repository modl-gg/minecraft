package gg.modl.minecraft.bridge.resource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BridgeResourceConsistencyTest {

    @Test
    void bridgeConfigCopiesStayConsistent() throws IOException {
        assertResourceCopiesStayConsistent("bridge-config.yml");
    }

    @Test
    void staffModeCopiesStayConsistent() throws IOException {
        assertResourceCopiesStayConsistent("staff_mode.yml");
    }

    @Test
    void bridgeLocaleCopiesStayConsistent() throws IOException {
        assertResourceCopiesStayConsistent("bridge_locale/en_US.yml");
    }

    private void assertResourceCopiesStayConsistent(String resourcePath) throws IOException {
        Path rootDir = rootDir();
        String bridgeCoreContent = readString(rootDir.resolve("bridge-core/src/main/resources").resolve(resourcePath));

        assertEquals(bridgeCoreContent,
                readString(rootDir.resolve("platforms/fabric/src/main/resources").resolve(resourcePath)));
        assertEquals(bridgeCoreContent,
                readString(rootDir.resolve("platforms/spigot/src/main/resources").resolve(resourcePath)));
    }

    private Path rootDir() {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        return projectDir.getFileName().toString().equals("bridge-core") ? projectDir.getParent() : projectDir;
    }

    private static String readString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
