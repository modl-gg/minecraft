package gg.modl.minecraft.velocity;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityCompatibilityTest {
    private static final String PLUGIN_CLASS = "/gg/modl/minecraft/velocity/VelocityPlugin.class";
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
    private static final int JAVA_17_CLASS_FILE_MAJOR = 61;
    private static final int CLASS_FILE_MINOR_VERSION_BYTES = 2;

    @Test
    void bytecodeStaysLoadableOnVelocity3() throws IOException {
        int major = classFileMajorVersion(PLUGIN_CLASS);
        assertTrue(
                major <= JAVA_17_CLASS_FILE_MAJOR,
                "One jar ships to both Velocity 3 and Velocity 4 proxies. Velocity 4 runs on Java 25 and loads "
                        + "older bytecode without complaint, but a Velocity 3 proxy on Java 17 cannot load past "
                        + "class-file major " + JAVA_17_CLASS_FILE_MAJOR + ", and these classes compile to major "
                        + major + ". Restore jvmReleaseOverrides[\"velocity\"] to 17 in the root build.gradle.kts, "
                        + "or drop Velocity 3 support deliberately and update this test."
        );
    }

    private static int classFileMajorVersion(String resource) throws IOException {
        InputStream resourceStream = VelocityCompatibilityTest.class.getResourceAsStream(resource);
        assertNotNull(resourceStream, "Missing " + resource + " on the test classpath");

        try (DataInputStream in = new DataInputStream(resourceStream)) {
            if (in.readInt() != CLASS_FILE_MAGIC) {
                throw new IOException(resource + " is not a class file");
            }
            in.skipBytes(CLASS_FILE_MINOR_VERSION_BYTES);
            return in.readUnsignedShort();
        }
    }
}
