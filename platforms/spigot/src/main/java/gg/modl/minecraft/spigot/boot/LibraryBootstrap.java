package gg.modl.minecraft.spigot.boot;

import com.alessiodp.libby.BukkitLibraryManager;
import com.alessiodp.libby.logging.LogLevel;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.boot.LibraryLoader;
import org.bukkit.plugin.java.JavaPlugin;

public class LibraryBootstrap {

    private final BukkitLibraryManager libraryManager;

    public LibraryBootstrap(JavaPlugin plugin) {
        this.libraryManager = new BukkitLibraryManager(plugin);
        this.libraryManager.setLogLevel(LogLevel.WARN);
        this.libraryManager.addMavenCentral();
        this.libraryManager.addRepository("https://nexus.modl.gg/repository/maven-releases/");
        this.libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        this.libraryManager.addRepository("https://jitpack.io");
    }

    public void loadRuntimeLibraries() {
        for (LibraryRecord record : Libraries.PROTO_DEPS_RELOCATED) loadLibrary(record);
        for (LibraryRecord record : Libraries.COMMON) loadLibrary(record);
        loadLibrary(Libraries.LAMP_COMMON);
        loadLibrary(Libraries.LAMP_BRIGADIER);
        loadLibrary(Libraries.LAMP_BUKKIT);
        loadLibrary(Libraries.SLF4J_API);
        loadLibrary(Libraries.SLF4J_SIMPLE);
        loadLibrary(Libraries.CIRRUS_SPIGOT);
        loadLibrary(Libraries.EXAMINATION_API);
        loadLibrary(Libraries.EXAMINATION_STRING);
        loadLibrary(Libraries.ADVENTURE_KEY);
        loadLibrary(Libraries.ADVENTURE_API);
        loadLibrary(Libraries.ADVENTURE_TEXT_SERIALIZER_LEGACY);
        loadLibrary(Libraries.ADVENTURE_TEXT_SERIALIZER_JSON);
        loadLibrary(Libraries.ADVENTURE_TEXT_SERIALIZER_GSON);
        loadLibrary(Libraries.ADVENTURE_TEXT_MINIMESSAGE);
    }

    public void loadPacketEventsLibraries() {
        loadLibrary(Libraries.PACKETEVENTS_API);
        loadLibrary(Libraries.PACKETEVENTS_NETTY);
        loadLibrary(Libraries.PACKETEVENTS_SPIGOT);
    }

    private void loadLibrary(LibraryRecord record) {
        libraryManager.loadLibrary(LibraryLoader.toLibrary(record));
    }
}
