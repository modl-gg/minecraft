package gg.modl.minecraft.fabric;

import com.alessiodp.libby.FabricLibraryManager;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.boot.LibraryLoader;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alessiodp.libby.logging.LogLevel;

public class ModlFabricMod implements DedicatedServerModInitializer {
    private static final String MOD_ID = "modl";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String IMPL_1_21_1 = "gg.modl.minecraft.fabric.v1_21_1.ModlFabricModImpl";
    private static final String IMPL_1_21_4 = "gg.modl.minecraft.fabric.v1_21_4.ModlFabricModImpl";
    private static final String IMPL_1_21_8 = "gg.modl.minecraft.fabric.v1_21_8.ModlFabricModImpl";
    private static final String IMPL_1_21_11 = "gg.modl.minecraft.fabric.v1_21_11.ModlFabricModImpl";
    private static final String IMPL_26 = "gg.modl.minecraft.fabric.v26.ModlFabricModImpl";
    private static final SupportedVersionRange[] SUPPORTED_VERSION_RANGES = {
            new SupportedVersionRange(1, 21, 1, 1, IMPL_1_21_1),
            new SupportedVersionRange(1, 21, 2, 6, IMPL_1_21_4),
            new SupportedVersionRange(1, 21, 7, 10, IMPL_1_21_8),
            new SupportedVersionRange(1, 21, 11, 11, IMPL_1_21_11),
            new SupportedVersionRange(26, null, 0, Integer.MAX_VALUE, IMPL_26)
    };

    @Override
    public void onInitializeServer() {
        String gameVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .orElseThrow(() -> new IllegalStateException("minecraft mod container missing"))
                .getMetadata().getVersion().getFriendlyString();

        loadLibraries();

        String implClass = selectImplementationClass(gameVersion);

        try {
            DedicatedServerModInitializer impl = (DedicatedServerModInitializer)
                    Class.forName(implClass).getConstructor().newInstance();
            impl.onInitializeServer();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load modl Fabric implementation for MC " + gameVersion, e);
        }
    }

    static String selectImplementationClass(String gameVersion) {
        MinecraftVersion version = MinecraftVersion.parse(gameVersion);
        for (SupportedVersionRange range : SUPPORTED_VERSION_RANGES) {
            if (range.matches(version)) {
                return range.implementationClass();
            }
        }
        throw new IllegalArgumentException("Unsupported Minecraft version: " + gameVersion);
    }

    private record MinecraftVersion(int major, int minor, int patch) {
        private static MinecraftVersion parse(String gameVersion) {
            if (gameVersion == null || gameVersion.trim().isEmpty()) {
                throw new IllegalArgumentException("Malformed Minecraft version: " + gameVersion);
            }

            String[] parts = gameVersion.split("\\.");
            if (parts.length < 2 || parts.length > 3) {
                throw new IllegalArgumentException("Malformed Minecraft version: " + gameVersion);
            }

            int major = parseVersionPart(gameVersion, parts[0]);
            int minor = parseVersionPart(gameVersion, parts[1]);
            if (major == 1 && minor == 21 && parts.length != 3) {
                throw new IllegalArgumentException("Malformed Minecraft version: " + gameVersion);
            }
            int patch = parts.length == 3 ? parseVersionPart(gameVersion, parts[2]) : 0;
            return new MinecraftVersion(major, minor, patch);
        }
    }

    private static int parseVersionPart(String gameVersion, String part) {
        if (part.isEmpty()) {
            throw new IllegalArgumentException("Malformed Minecraft version: " + gameVersion);
        }

        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed Minecraft version: " + gameVersion, e);
        }
    }

    private record SupportedVersionRange(
            int major,
            Integer minor,
            int minPatch,
            int maxPatch,
            String implementationClass
    ) {
        private boolean matches(MinecraftVersion version) {
            return version.major() == major
                    && (minor == null || version.minor() == minor)
                    && version.patch() >= minPatch
                    && version.patch() <= maxPatch;
        }
    }

    private void loadLibraries() {
        FabricLibraryManager libraryManager = new FabricLibraryManager(MOD_ID, LOGGER);
        libraryManager.setLogLevel(LogLevel.WARN);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://nexus.modl.gg/repository/maven-releases/");
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-snapshots/");
        libraryManager.addRepository("https://jitpack.io");
        libraryManager.addRepository("https://repo.aikar.co/content/groups/aikar/");

        for (LibraryRecord record : Libraries.PROTO_DEPS) loadLibrary(libraryManager, record);
        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.SLF4J_API);
        loadLibrary(libraryManager, Libraries.SLF4J_SIMPLE);
        loadLibrary(libraryManager, Libraries.CIRRUS_FABRIC);
        loadLibrary(libraryManager, Libraries.ADVENTURE_KEY);
        loadLibrary(libraryManager, Libraries.ADVENTURE_API);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_LEGACY);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_MINIMESSAGE);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_JSON);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_GSON);
        loadLibrary(libraryManager, Libraries.EXAMINATION_API);
        loadLibrary(libraryManager, Libraries.EXAMINATION_STRING);
        loadLibrary(libraryManager, Libraries.LAMP_COMMON);
        loadLibrary(libraryManager, Libraries.LAMP_BRIGADIER);
        loadLibrary(libraryManager, Libraries.LAMP_FABRIC);
    }

    private void loadLibrary(FabricLibraryManager libraryManager, LibraryRecord record) {
        libraryManager.loadLibrary(LibraryLoader.toLibrary(record));
    }
}
