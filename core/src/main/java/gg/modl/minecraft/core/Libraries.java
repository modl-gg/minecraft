package gg.modl.minecraft.core;

import gg.modl.minecraft.api.LibraryRecord;

import java.util.Arrays;
import java.util.List;

/**
 * Defines libraries to be loaded at runtime via libby.
 * These libraries are downloaded and loaded dynamically instead of being shaded into the JAR.
 *
 * Note: Libraries loaded without relocation use their original package names.
 * Libraries that may conflict with server installations should use relocation.
 */
public final class Libraries {

    private Libraries() {
        // Utility class
    }

    // SnakeYAML - YAML parsing library (no relocation - uses org.yaml.snakeyaml)
    public static final LibraryRecord SNAKEYAML = LibraryRecord.of(
            "org{}yaml",
            "snakeyaml",
            "2.3",
            "snakeyaml"
    );

    // Gson - JSON serialization library (no relocation - uses com.google.gson)
    public static final LibraryRecord GSON = LibraryRecord.of(
            "com{}google{}code{}gson",
            "gson",
            "2.12.1",
            "gson"
    );

    // Apache HttpClient5 - HTTP client library
    public static final LibraryRecord HTTPCLIENT5 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}client5",
            "httpclient5",
            "5.2.1",
            "httpclient5"
    );

    // Apache HttpCore5 - Required by HttpClient5
    public static final LibraryRecord HTTPCORE5 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}core5",
            "httpcore5",
            "5.2.4",
            "httpcore5"
    );

    // Apache HttpCore5 H2 - HTTP/2 support
    public static final LibraryRecord HTTPCORE5_H2 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}core5",
            "httpcore5-h2",
            "5.2.4",
            "httpcore5-h2"
    );

    // PacketEvents API (must be loaded before platform-specific implementations)
    public static final LibraryRecord PACKETEVENTS_API = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-api",
            "2.11.2",
            "packetevents-api"
    );

    // PacketEvents Netty (required by platform implementations)
    public static final LibraryRecord PACKETEVENTS_NETTY = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-netty-common",
            "2.11.2",
            "packetevents-netty-common"
    );

    // PacketEvents Spigot
    public static final LibraryRecord PACKETEVENTS_SPIGOT = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-spigot",
            "2.11.2",
            "packetevents-spigot"
    );

    // PacketEvents BungeeCord
    public static final LibraryRecord PACKETEVENTS_BUNGEE = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-bungeecord",
            "2.11.2",
            "packetevents-bungeecord"
    );

    // PacketEvents Velocity
    public static final LibraryRecord PACKETEVENTS_VELOCITY = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-velocity",
            "2.11.2",
            "packetevents-velocity"
    );

    public static final LibraryRecord ADVENTURE_NBT = LibraryRecord.of(
        "net{}kyori",
        "adventure-nbt",
        "4.25.0",
        "adventure-nbt"
    );

    /**
     * Common libraries loaded by all platforms.
     * These are the core dependencies needed for the plugin to function.
     */
    public static final List<LibraryRecord> COMMON = Arrays.asList(
            SNAKEYAML,
            GSON,
            HTTPCLIENT5,
            HTTPCORE5,
            HTTPCORE5_H2,
            ADVENTURE_NBT
    );
}
