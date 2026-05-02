package gg.modl.minecraft.core;

import gg.modl.minecraft.api.LibraryRecord;

import java.util.Arrays;
import java.util.List;

public final class Libraries {

    private Libraries() {}

    private static final String[][] CIRRUS_PACKETEVENTS_REVERSE_RELOCATIONS = {
            {"gg{}modl{}libs{}packetevents{}api", "com{}github{}retrooper{}packetevents"},
            {"gg{}modl{}libs{}packetevents{}impl", "io{}github{}retrooper{}packetevents"}
    };

    // Version uses timestamp instead of "-SNAPSHOT" because libby 1.3.1 re-downloads SNAPSHOT versions every startup
    public static final LibraryRecord SNAKEYAML = LibraryRecord.of(
            "org{}yaml",
            "snakeyaml",
            LibraryVersions.SNAKEYAML,
            "snakeyaml",
            LibraryVersions.SNAKEYAML_CHECKSUM
    ),
    GSON = LibraryRecord.of(
            "com{}google{}code{}gson",
            "gson",
            LibraryVersions.GSON,
            "gson",
            LibraryVersions.GSON_CHECKSUM
    ),
    HTTPCLIENT5 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}client5",
            "httpclient5",
            LibraryVersions.HTTPCLIENT5,
            "httpclient5",
            LibraryVersions.HTTPCLIENT5_CHECKSUM
    ),
    HTTPCORE5 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}core5",
            "httpcore5",
            LibraryVersions.HTTPCORE5,
            "httpcore5",
            LibraryVersions.HTTPCORE5_CHECKSUM
    ),
    HTTPCORE5_H2 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}core5",
            "httpcore5-h2",
            LibraryVersions.HTTPCORE5_H2,
            "httpcore5-h2",
            LibraryVersions.HTTPCORE5_H2_CHECKSUM
    ),
    JAVA_WEBSOCKET = LibraryRecord.of(
            "org{}java-websocket",
            "Java-WebSocket",
            LibraryVersions.JAVA_WEBSOCKET,
            "Java-WebSocket",
            LibraryVersions.JAVA_WEBSOCKET_CHECKSUM
    ),
    PACKETEVENTS_API = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-api",
            LibraryVersions.PACKETEVENTS_API,
            "packetevents-api",
            LibraryVersions.PACKETEVENTS_API_CHECKSUM
    ),
    PACKETEVENTS_NETTY = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-netty-common",
            LibraryVersions.PACKETEVENTS_NETTY,
            "packetevents-netty-common",
            LibraryVersions.PACKETEVENTS_NETTY_CHECKSUM
    ),
    PACKETEVENTS_SPIGOT = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-spigot",
            LibraryVersions.PACKETEVENTS_SPIGOT,
            "packetevents-spigot",
            LibraryVersions.PACKETEVENTS_SPIGOT_CHECKSUM
    ),
    PACKETEVENTS_BUNGEE = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-bungeecord",
            LibraryVersions.PACKETEVENTS_BUNGEE,
            "packetevents-bungeecord",
            LibraryVersions.PACKETEVENTS_BUNGEE_CHECKSUM
    ),
    PACKETEVENTS_VELOCITY = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-velocity",
            LibraryVersions.PACKETEVENTS_VELOCITY,
            "packetevents-velocity",
            LibraryVersions.PACKETEVENTS_VELOCITY_CHECKSUM
    ),
    PACKETEVENTS_FABRIC_COMMON = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-fabric-common",
            LibraryVersions.PACKETEVENTS_FABRIC_COMMON,
            "packetevents-fabric-common",
            LibraryVersions.PACKETEVENTS_FABRIC_COMMON_CHECKSUM
    ),
    PACKETEVENTS_FABRIC_INTERMEDIARY = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-fabric-intermediary",
            LibraryVersions.PACKETEVENTS_FABRIC_INTERMEDIARY,
            "packetevents-fabric-intermediary",
            LibraryVersions.PACKETEVENTS_FABRIC_INTERMEDIARY_CHECKSUM
    ),
    PACKETEVENTS_FABRIC_OFFICIAL = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-fabric-official",
            LibraryVersions.PACKETEVENTS_FABRIC_OFFICIAL,
            "packetevents-fabric-official",
            LibraryVersions.PACKETEVENTS_FABRIC_OFFICIAL_CHECKSUM
    ),
    ADVENTURE_NBT = LibraryRecord.of(
            "net{}kyori",
            "adventure-nbt",
            LibraryVersions.ADVENTURE_NBT,
            "adventure-nbt",
            LibraryVersions.ADVENTURE_NBT_CHECKSUM
    ),
    LAMP_COMMON = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.common",
            LibraryVersions.LAMP_COMMON,
            "lamp-common",
            LibraryVersions.LAMP_COMMON_CHECKSUM
    ),
    LAMP_BRIGADIER = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.brigadier",
            LibraryVersions.LAMP_BRIGADIER,
            "lamp-brigadier",
            LibraryVersions.LAMP_BRIGADIER_CHECKSUM
    ),
    LAMP_BUKKIT = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.bukkit",
            LibraryVersions.LAMP_BUKKIT,
            "lamp-bukkit",
            LibraryVersions.LAMP_BUKKIT_CHECKSUM
    ),
    LAMP_VELOCITY = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.velocity",
            LibraryVersions.LAMP_VELOCITY,
            "lamp-velocity",
            LibraryVersions.LAMP_VELOCITY_CHECKSUM
    ),
    LAMP_BUNGEE = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.bungee",
            LibraryVersions.LAMP_BUNGEE,
            "lamp-bungee",
            LibraryVersions.LAMP_BUNGEE_CHECKSUM
    ),
    LAMP_FABRIC = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.fabric",
            LibraryVersions.LAMP_FABRIC,
            "lamp-fabric",
            LibraryVersions.LAMP_FABRIC_CHECKSUM
    ),
    SLF4J_API = LibraryRecord.of(
            "org{}slf4j",
            "slf4j-api",
            LibraryVersions.SLF4J_API,
            "slf4j-api",
            LibraryVersions.SLF4J_API_CHECKSUM
    ),
    SLF4J_SIMPLE = LibraryRecord.of(
            "org{}slf4j",
            "slf4j-simple",
            LibraryVersions.SLF4J_SIMPLE,
            "slf4j-simple",
            LibraryVersions.SLF4J_SIMPLE_CHECKSUM
    ),
    CIRRUS_SPIGOT = LibraryRecord.of(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-spigot",
            LibraryVersions.CIRRUS_SPIGOT,
            "cirrus-spigot",
            LibraryVersions.CIRRUS_SPIGOT_CHECKSUM,
            CIRRUS_PACKETEVENTS_REVERSE_RELOCATIONS
    ),
    CIRRUS_VELOCITY = LibraryRecord.of(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-velocity",
            LibraryVersions.CIRRUS_VELOCITY,
            "cirrus-velocity",
            LibraryVersions.CIRRUS_VELOCITY_CHECKSUM,
            CIRRUS_PACKETEVENTS_REVERSE_RELOCATIONS
    ),
    CIRRUS_BUNGEECORD = LibraryRecord.of(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-bungeecord",
            LibraryVersions.CIRRUS_BUNGEECORD,
            "cirrus-bungeecord",
            LibraryVersions.CIRRUS_BUNGEECORD_CHECKSUM,
            CIRRUS_PACKETEVENTS_REVERSE_RELOCATIONS
    ),
    CIRRUS_FABRIC = LibraryRecord.of(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-fabric",
            LibraryVersions.CIRRUS_FABRIC,
            "cirrus-fabric",
            LibraryVersions.CIRRUS_FABRIC_CHECKSUM
    ),
    ADVENTURE_KEY = LibraryRecord.of(
            "net{}kyori",
            "adventure-key",
            LibraryVersions.ADVENTURE_KEY,
            "adventure-key",
            LibraryVersions.ADVENTURE_KEY_CHECKSUM
    ),
    EXAMINATION_API = LibraryRecord.of(
            "net{}kyori",
            "examination-api",
            LibraryVersions.EXAMINATION_API,
            "examination-api",
            LibraryVersions.EXAMINATION_API_CHECKSUM
    ),
    EXAMINATION_STRING = LibraryRecord.of(
            "net{}kyori",
            "examination-string",
            LibraryVersions.EXAMINATION_STRING,
            "examination-string",
            LibraryVersions.EXAMINATION_STRING_CHECKSUM
    ),
    ADVENTURE_API = LibraryRecord.of(
            "net{}kyori",
            "adventure-api",
            LibraryVersions.ADVENTURE_API,
            "adventure-api",
            LibraryVersions.ADVENTURE_API_CHECKSUM
    ),
    ADVENTURE_TEXT_SERIALIZER_LEGACY = LibraryRecord.of(
            "net{}kyori",
            "adventure-text-serializer-legacy",
            LibraryVersions.ADVENTURE_TEXT_SERIALIZER_LEGACY,
            "adventure-text-serializer-legacy",
            LibraryVersions.ADVENTURE_TEXT_SERIALIZER_LEGACY_CHECKSUM
    ),
    ADVENTURE_TEXT_MINIMESSAGE = LibraryRecord.of(
            "net{}kyori",
            "adventure-text-minimessage",
            LibraryVersions.ADVENTURE_TEXT_MINIMESSAGE,
            "adventure-text-minimessage",
            LibraryVersions.ADVENTURE_TEXT_MINIMESSAGE_CHECKSUM
    ),
    ADVENTURE_TEXT_SERIALIZER_JSON = LibraryRecord.of(
            "net{}kyori",
            "adventure-text-serializer-json",
            LibraryVersions.ADVENTURE_TEXT_SERIALIZER_JSON,
            "adventure-text-serializer-json",
            LibraryVersions.ADVENTURE_TEXT_SERIALIZER_JSON_CHECKSUM
    ),
    ADVENTURE_TEXT_SERIALIZER_GSON = LibraryRecord.of(
            "net{}kyori",
            "adventure-text-serializer-gson",
            LibraryVersions.ADVENTURE_TEXT_SERIALIZER_GSON,
            "adventure-text-serializer-gson",
            LibraryVersions.ADVENTURE_TEXT_SERIALIZER_GSON_CHECKSUM
    ),

    PROTOBUF_JAVA = LibraryRecord.of(
            "com{}google{}protobuf",
            "protobuf-java",
            LibraryVersions.PROTOBUF_JAVA,
            "protobuf-java",
            LibraryVersions.PROTOBUF_JAVA_CHECKSUM
    ),
    PROTOBUF_JAVA_UTIL = LibraryRecord.of(
            "com{}google{}protobuf",
            "protobuf-java-util",
            LibraryVersions.PROTOBUF_JAVA_UTIL,
            "protobuf-java-util",
            LibraryVersions.PROTOBUF_JAVA_UTIL_CHECKSUM
    ),
    GUAVA = LibraryRecord.of(
            "com{}google{}guava",
            "guava",
            LibraryVersions.GUAVA,
            "guava",
            LibraryVersions.GUAVA_CHECKSUM
    ),
    FAILUREACCESS = LibraryRecord.of(
            "com{}google{}guava",
            "failureaccess",
            LibraryVersions.FAILUREACCESS,
            "failureaccess",
            LibraryVersions.FAILUREACCESS_CHECKSUM
    ),
    PROTOVALIDATE = LibraryRecord.of(
            "build{}buf",
            "protovalidate",
            LibraryVersions.PROTOVALIDATE,
            "protovalidate",
            LibraryVersions.PROTOVALIDATE_CHECKSUM
    ),
    CEL_CORE = LibraryRecord.of(
            "org{}projectnessie{}cel",
            "cel-core",
            LibraryVersions.CEL_CORE,
            "cel-core",
            LibraryVersions.CEL_CORE_CHECKSUM
    ),
    CEL_GENERATED_ANTLR = LibraryRecord.of(
            "org{}projectnessie{}cel",
            "cel-generated-antlr",
            LibraryVersions.CEL_GENERATED_ANTLR,
            "cel-generated-antlr",
            LibraryVersions.CEL_GENERATED_ANTLR_CHECKSUM
    ),
    CEL_GENERATED_PB = LibraryRecord.of(
            "org{}projectnessie{}cel",
            "cel-generated-pb",
            LibraryVersions.CEL_GENERATED_PB,
            "cel-generated-pb",
            LibraryVersions.CEL_GENERATED_PB_CHECKSUM
    ),
    AGRONA = LibraryRecord.of(
            "org{}agrona",
            "agrona",
            LibraryVersions.AGRONA,
            "agrona",
            LibraryVersions.AGRONA_CHECKSUM
    ),
    IPADDRESS = LibraryRecord.of(
            "com{}github{}seancfoley",
            "ipaddress",
            LibraryVersions.IPADDRESS,
            "ipaddress",
            LibraryVersions.IPADDRESS_CHECKSUM
    ),
    JAKARTA_MAIL_API = LibraryRecord.of(
            "jakarta{}mail",
            "jakarta.mail-api",
            LibraryVersions.JAKARTA_MAIL_API,
            "jakarta-mail-api",
            LibraryVersions.JAKARTA_MAIL_API_CHECKSUM
    ),
    JAKARTA_ACTIVATION_API = LibraryRecord.of(
            "jakarta{}activation",
            "jakarta.activation-api",
            LibraryVersions.JAKARTA_ACTIVATION_API,
            "jakarta-activation-api",
            LibraryVersions.JAKARTA_ACTIVATION_API_CHECKSUM
    ),
    MODL_PROTO = LibraryRecord.of(
            "gg{}modl",
            "proto",
            LibraryVersions.MODL_PROTO,
            "proto",
            LibraryVersions.MODL_PROTO_CHECKSUM
    );

    public static final List<LibraryRecord> COMMON = Arrays.asList(
            SNAKEYAML,
            GSON,
            HTTPCLIENT5,
            HTTPCORE5,
            HTTPCORE5_H2,
            JAVA_WEBSOCKET,
            ADVENTURE_NBT
    );

    public static final List<LibraryRecord> PROTO_DEPS = Arrays.asList(
            PROTOBUF_JAVA,
            PROTOBUF_JAVA_UTIL,
            GUAVA,
            FAILUREACCESS,
            PROTOVALIDATE,
            CEL_CORE,
            CEL_GENERATED_ANTLR,
            CEL_GENERATED_PB,
            AGRONA,
            IPADDRESS,
            JAKARTA_MAIL_API,
            JAKARTA_ACTIVATION_API,
            MODL_PROTO
    );
}
