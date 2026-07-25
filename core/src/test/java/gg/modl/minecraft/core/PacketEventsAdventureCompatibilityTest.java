package gg.modl.minecraft.core;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adventure 5 (shipped by Velocity 4.1.0) turned {@code net.kyori.adventure.nbt.BinaryTagType} from an
 * abstract class into a sealed interface. Any PacketEvents build that calls a BinaryTagType method
 * directly compiles that call into a CONSTANT_Methodref / invokevirtual, which fails with
 * {@code IncompatibleClassChangeError: Found interface net.kyori.adventure.nbt.BinaryTagType, but class
 * was expected} as soon as PacketEvents loads on a proxy shipping Adventure 5.
 *
 * <p>The pinned PacketEvents build therefore has to reach BinaryTagType purely through reflection. This
 * asserts that invariant against the jar actually resolved by {@code packetevents.version}, so a
 * downgrade cannot silently reintroduce the crash.
 */
class PacketEventsAdventureCompatibilityTest {
    private static final String ADVENTURE_NBT_UTIL =
            "/com/github/retrooper/packetevents/util/adventure/AdventureNbtUtil.class";
    private static final String BINARY_TAG_TYPE = "net/kyori/adventure/nbt/BinaryTagType";

    @Test
    void adventureNbtUtilNeverInvokesBinaryTagTypeAsAClass() throws IOException {
        ConstantPool constantPool = ConstantPool.read(ADVENTURE_NBT_UTIL);

        // Guards against the assertion below going vacuous if PacketEvents ever renames the type.
        assertTrue(
                constantPool.referencedClasses.contains(BINARY_TAG_TYPE),
                ADVENTURE_NBT_UTIL + " no longer references " + BINARY_TAG_TYPE + "; this test needs updating"
        );

        assertFalse(
                constantPool.classMethodOwners.contains(BINARY_TAG_TYPE),
                "AdventureNbtUtil invokes " + BINARY_TAG_TYPE + " as a class, but Adventure 5 declares it as an "
                        + "interface. This crashes PacketEvents on Velocity 4.1.0 with IncompatibleClassChangeError. "
                        + "Raise packetevents.version to a build that resolves BinaryTagType reflectively."
        );
    }

    /** Class names referenced by a class file, split by how its method references are encoded. */
    private static final class ConstantPool {
        private static final int TAG_UTF8 = 1;
        private static final int TAG_CLASS = 7;
        private static final int TAG_METHODREF = 10;

        private final Set<String> referencedClasses;
        private final Set<String> classMethodOwners;

        private ConstantPool(Set<String> referencedClasses, Set<String> classMethodOwners) {
            this.referencedClasses = referencedClasses;
            this.classMethodOwners = classMethodOwners;
        }

        static ConstantPool read(String resource) throws IOException {
            InputStream resourceStream = PacketEventsAdventureCompatibilityTest.class.getResourceAsStream(resource);
            assertNotNull(resourceStream, "Missing " + resource + " on the test classpath");

            try (DataInputStream in = new DataInputStream(resourceStream)) {
                if (in.readInt() != 0xCAFEBABE) {
                    throw new IOException(resource + " is not a class file");
                }
                in.skipBytes(4); // minor + major version

                int count = in.readUnsignedShort();
                String[] utf8 = new String[count];
                int[] classNameIndex = new int[count];
                int[] methodRefClassIndex = new int[count];
                Arrays.fill(classNameIndex, -1);
                Arrays.fill(methodRefClassIndex, -1);

                for (int i = 1; i < count; i++) {
                    int tag = in.readUnsignedByte();
                    switch (tag) {
                        case TAG_UTF8:
                            utf8[i] = in.readUTF();
                            break;
                        case TAG_CLASS:
                            classNameIndex[i] = in.readUnsignedShort();
                            break;
                        case TAG_METHODREF:
                            methodRefClassIndex[i] = in.readUnsignedShort();
                            in.skipBytes(2); // name and type index
                            break;
                        case 8:  // String
                        case 16: // MethodType
                        case 19: // Module
                        case 20: // Package
                            in.skipBytes(2);
                            break;
                        case 15: // MethodHandle
                            in.skipBytes(3);
                            break;
                        case 3:  // Integer
                        case 4:  // Float
                        case 9:  // Fieldref
                        case 11: // InterfaceMethodref
                        case 12: // NameAndType
                        case 17: // Dynamic
                        case 18: // InvokeDynamic
                            in.skipBytes(4);
                            break;
                        case 5: // Long
                        case 6: // Double
                            in.skipBytes(8);
                            i++; // eight-byte constants occupy two pool slots
                            break;
                        default:
                            throw new IOException("Unknown constant pool tag " + tag + " in " + resource);
                    }
                }

                Set<String> referencedClasses = new HashSet<>();
                Set<String> classMethodOwners = new HashSet<>();
                for (int i = 1; i < count; i++) {
                    if (classNameIndex[i] >= 0) {
                        referencedClasses.add(utf8[classNameIndex[i]]);
                    }
                    if (methodRefClassIndex[i] >= 0) {
                        classMethodOwners.add(utf8[classNameIndex[methodRefClassIndex[i]]]);
                    }
                }
                return new ConstantPool(referencedClasses, classMethodOwners);
            }
        }
    }
}
