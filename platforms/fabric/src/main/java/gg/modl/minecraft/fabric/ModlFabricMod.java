package gg.modl.minecraft.fabric;

import com.alessiodp.libby.FabricLibraryManager;
import com.alessiodp.libby.Library;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.Libraries;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModlFabricMod implements DedicatedServerModInitializer {
    private static final String MOD_ID = "modl";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        loadLibraries();

        String gameVersion = FabricLoader.getInstance()
                .getModContainer("minecraft").get()
                .getMetadata().getVersion().getFriendlyString();

        String implClass;
        if (gameVersion.startsWith("26.")) {
            implClass = "gg.modl.minecraft.fabric.v26.ModlFabricModImpl";
        } else if (gameVersion.startsWith("1.21.")) {
            int minor = parseMinorVersion(gameVersion);
            if (minor >= 5) {
                implClass = "gg.modl.minecraft.fabric.v1_21_11.ModlFabricModImpl";
            } else if (minor >= 2) {
                implClass = "gg.modl.minecraft.fabric.v1_21_4.ModlFabricModImpl";
            } else {
                implClass = "gg.modl.minecraft.fabric.v1_21_1.ModlFabricModImpl";
            }
        } else {
            implClass = "gg.modl.minecraft.fabric.v1_21_11.ModlFabricModImpl";
        }

        try {
            DedicatedServerModInitializer impl = (DedicatedServerModInitializer)
                    Class.forName(implClass).getConstructor().newInstance();
            impl.onInitializeServer();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load modl Fabric implementation for MC " + gameVersion, e);
        }
    }

    private static int parseMinorVersion(String gameVersion) {
        String[] parts = gameVersion.split("\\.");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private void loadLibraries() {
        FabricLibraryManager libraryManager = new FabricLibraryManager(MOD_ID, LOGGER);
        libraryManager.setLogLevel(com.alessiodp.libby.logging.LogLevel.WARN);
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
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_FABRIC_COMMON);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_FABRIC_INTERMEDIARY);
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
        Library.Builder builder = Library.builder()
                .groupId(record.getGroupId())
                .artifactId(record.getArtifactId())
                .version(record.getVersion());

        if (record.hasRelocations()) {
            for (String[] relocation : record.getRelocations()) {
                builder.relocate(relocation[0], relocation[1]);
            }
        }
        if (record.getUrl() != null) builder.url(record.getUrl());
        if (record.hasChecksum()) builder.checksumFromBase64(record.getChecksum());

        libraryManager.loadLibrary(builder.build());
    }
}
