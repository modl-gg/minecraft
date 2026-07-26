package gg.modl.minecraft.velocity;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A single jar ships to both Velocity 3 and Velocity 4 proxies.
 *
 * <p>Velocity 4 requires Java 25 and loads older bytecode without complaint, but a Velocity 3 proxy
 * on Java 17 cannot load anything newer. Raising this module's target to match the Velocity 4 API
 * therefore drops Velocity 3 support silently — the build stays green and the breakage only shows up
 * as a class-version error on someone else's proxy.
 */
class VelocityCompatibilityTest {
    private static final String PLUGIN_CLASS = "/gg/modl/minecraft/velocity/VelocityPlugin.class";
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
    private static final int JAVA_17_CLASS_FILE_MAJOR = 61;

    @Test
    void bytecodeStaysLoadableOnVelocity3() throws IOException {
        int major = classFileMajorVersion(PLUGIN_CLASS);
        assertTrue(
                major <= JAVA_17_CLASS_FILE_MAJOR,
                "Velocity platform classes compile to class-file major " + major + ", but a Velocity 3 proxy "
                        + "on Java 17 can only load up to " + JAVA_17_CLASS_FILE_MAJOR + ". Lower the module's "
                        + "targetCompatibility, or drop Velocity 3 support deliberately and update this test."
        );
    }

    private static int classFileMajorVersion(String resource) throws IOException {
        InputStream resourceStream = VelocityCompatibilityTest.class.getResourceAsStream(resource);
        assertNotNull(resourceStream, "Missing " + resource + " on the test classpath");

        try (DataInputStream in = new DataInputStream(resourceStream)) {
            if (in.readInt() != CLASS_FILE_MAGIC) {
                throw new IOException(resource + " is not a class file");
            }
            in.readUnsignedShort(); // minor version
            return in.readUnsignedShort();
        }
    }
}
