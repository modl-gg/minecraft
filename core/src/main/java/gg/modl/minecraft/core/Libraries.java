package gg.modl.minecraft.core;

import gg.modl.minecraft.api.LibraryRecord;

import java.util.Arrays;
import java.util.List;

public final class Libraries {

    private Libraries() {}

    private static final String[][] PACKETEVENTS_RELOCATIONS = {
            {"com{}github{}retrooper{}packetevents", "gg{}modl{}libs{}packetevents{}api"},
            {"io{}github{}retrooper{}packetevents", "gg{}modl{}libs{}packetevents{}impl"}
    };

    // Version uses timestamp instead of "-SNAPSHOT" because libby 1.3.1 re-downloads SNAPSHOT versions every startup
    public static final LibraryRecord SNAKEYAML = LibraryRecord.of(
            "org{}yaml",
            "snakeyaml",
            "2.3",
            "snakeyaml",
            "Y6dv5mtlI2C9TCwQfm8CWNqn1LtJIAi6jCb80jD/kUY="
    ),
    GSON = LibraryRecord.of(
            "com{}google{}code{}gson",
            "gson",
            "2.12.1",
            "gson",
            "6+4T1ft0d81/HMAQ4MNW34yoBwlxUkjal/eeNcy0++w="
    ),
    HTTPCLIENT5 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}client5",
            "httpclient5",
            "5.2.1",
            "httpclient5",
            "k1Xzh2uvgv7BPO0iwSti1XU2Iwg2QG01lFkSjk9z7VE="
    ),
    HTTPCORE5 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}core5",
            "httpcore5",
            "5.2.4",
            "httpcore5",
            "p/YklhE/ZvnifCa4TET1zkVVxicAg83y1F8lUzbNUq8="
    ),
    HTTPCORE5_H2 = LibraryRecord.of(
            "org{}apache{}httpcomponents{}core5",
            "httpcore5-h2",
            "5.2.4",
            "httpcore5-h2",
            "3BqV5z6wTbk0UVM9OQzgLFOzAaENw0PQjIYvKTSz0w4="
    ),
    PACKETEVENTS_API = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-api",
            "2.12.0",
            "packetevents-api",
            null,
            PACKETEVENTS_RELOCATIONS
    ),
    PACKETEVENTS_NETTY = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-netty-common",
            "2.12.0",
            "packetevents-netty-common",
            null,
            PACKETEVENTS_RELOCATIONS
    ),
    PACKETEVENTS_SPIGOT = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-spigot",
            "2.12.0",
            "packetevents-spigot",
            null,
            PACKETEVENTS_RELOCATIONS
    ),
    PACKETEVENTS_BUNGEE = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-bungeecord",
            "2.12.0",
            "packetevents-bungeecord",
            null,
            PACKETEVENTS_RELOCATIONS
    ),
    PACKETEVENTS_VELOCITY = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-velocity",
            "2.12.0",
            "packetevents-velocity",
            null,
            PACKETEVENTS_RELOCATIONS
    ),
    PACKETEVENTS_FABRIC_COMMON = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-fabric-common",
            "2.12.0",
            "packetevents-fabric-common",
            null,
            PACKETEVENTS_RELOCATIONS
    ),
    PACKETEVENTS_FABRIC_INTERMEDIARY = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-fabric-intermediary",
            "2.12.0",
            "packetevents-fabric-intermediary",
            null,
            PACKETEVENTS_RELOCATIONS
    ),
    PACKETEVENTS_FABRIC_OFFICIAL = LibraryRecord.of(
            "gg{}modl{}minecraft{}packetevents",
            "packetevents-fabric-official",
            "2.12.0",
            "packetevents-fabric-official",
            null,
            PACKETEVENTS_RELOCATIONS
    ),
    ADVENTURE_NBT = LibraryRecord.of(
            "net{}kyori",
            "adventure-nbt",
            "4.25.0",
            "adventure-nbt",
            "jubaYh9JbxbGGd5uxcC5wUDOrJuJwqWzN3k429VaFi4="
    ),
    LAMP_COMMON = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.common",
            "4.0.0-rc.16",
            "lamp-common"
    ),
    LAMP_BRIGADIER = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.brigadier",
            "4.0.0-rc.16",
            "lamp-brigadier"
    ),
    LAMP_BUKKIT = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.bukkit",
            "4.0.0-rc.16",
            "lamp-bukkit"
    ),
    LAMP_VELOCITY = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.velocity",
            "4.0.0-rc.16",
            "lamp-velocity"
    ),
    LAMP_BUNGEE = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.bungee",
            "4.0.0-rc.16",
            "lamp-bungee"
    ),
    LAMP_FABRIC = LibraryRecord.of(
            "io{}github{}revxrsal",
            "lamp.fabric",
            "4.0.0-rc.16",
            "lamp-fabric"
    ),
    SLF4J_API = LibraryRecord.of(
            "org{}slf4j",
            "slf4j-api",
            "2.0.16",
            "slf4j-api",
            "oSV43eG6AL2bgW04iguHmSjQC6s8g8JA9wE79BlsV5o="
    ),
    SLF4J_SIMPLE = LibraryRecord.of(
            "org{}slf4j",
            "slf4j-simple",
            "2.0.16",
            "slf4j-simple",
            "7/wyAYZYvqCdHgjH0QYMytRsCGlg9YPQfdf/6cEXKkc="
    ),
    CIRRUS_SPIGOT = LibraryRecord.of(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-spigot",
            "4.2.2",
            "cirrus-spigot",
            "wyJoSnytFIL4LzFV/OMRdN/trYhlCv31X/rr0Nwj3ZA="
    ),
    CIRRUS_VELOCITY = LibraryRecord.of(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-velocity",
            "4.2.2",
            "cirrus-velocity",
            "4vX/vC30hsHONhs8eCc0wOVcU4MU41EDVgPFQCDOux0="
    ),
    CIRRUS_BUNGEECORD = LibraryRecord.of(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-bungeecord",
            "4.2.2",
            "cirrus-bungeecord",
            "5aO4KvA50sQGYUBQZGJs5q0NwLzvA24ZSNvofXw5RBo="
    ),
    CIRRUS_FABRIC = LibraryRecord.of(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-fabric",
            "4.2.2",
            "cirrus-fabric",
            "OHcOxe8/KuzsF2rnOkhV79o3fKZLaEHd8ilDWlr2wqI="
    ),
    ADVENTURE_KEY = LibraryRecord.of(
            "net{}kyori",
            "adventure-key",
            "4.26.1",
            "adventure-key",
            "7sFy1j23e0Drer7rJfZe7eqJvTAmTQV7aLEv7Lcxvl4="
    ),
    EXAMINATION_API = LibraryRecord.of(
            "net{}kyori",
            "examination-api",
            "1.3.0",
            "examination-api",
            "ySN//ssFQo9u/4YhYkascM4LR7BMCOp8o1Ag/eV/hJI="
    ),
    EXAMINATION_STRING = LibraryRecord.of(
            "net{}kyori",
            "examination-string",
            "1.3.0",
            "examination-string",
            "fQH8JaS7OvDhZiaFRV9FQfv0YmIW6lhG5FXBSR4Va4w="
    ),
    ADVENTURE_API = LibraryRecord.of(
            "net{}kyori",
            "adventure-api",
            "4.26.1",
            "adventure-api",
            "VR5Ta56oaPMOcseQCjCbNRJO59SIn6OzrtCRApl1GiY="
    ),
    ADVENTURE_TEXT_SERIALIZER_LEGACY = LibraryRecord.of(
            "net{}kyori",
            "adventure-text-serializer-legacy",
            "4.26.1",
            "adventure-text-serializer-legacy",
            "chEHvCE1ckVN8b++Q426Yw4wRXVQx1C3oVSzXcMmSqg="
    ),
    ADVENTURE_TEXT_MINIMESSAGE = LibraryRecord.of(
            "net{}kyori",
            "adventure-text-minimessage",
            "4.26.1",
            "adventure-text-minimessage",
            "HUNFHpr0cyUtyK8+gIQjjVzmitQ68OO3OD6z1LY//58="
    ),
    ADVENTURE_TEXT_SERIALIZER_JSON = LibraryRecord.of(
            "net{}kyori",
            "adventure-text-serializer-json",
            "4.26.1",
            "adventure-text-serializer-json",
            "VcZLQzPV0paKASW48p2ufPuhU5LV+Z94266nEcsNTcI="
    ),
    ADVENTURE_TEXT_SERIALIZER_GSON = LibraryRecord.of(
            "net{}kyori",
            "adventure-text-serializer-gson",
            "4.26.1",
            "adventure-text-serializer-gson",
            "5KkI3txKy0MFCD2RbTYt3Csh7PRS1XegvmZSgL3a6fw="
    ),

    PROTOBUF_JAVA = LibraryRecord.of(
            "com{}google{}protobuf",
            "protobuf-java",
            "4.34.1",
            "protobuf-java",
            "rgRZAwtUpvMFitNJqgH8Lty7LUqEtzaZZ1u9El3y6W0="
    ),
    PROTOBUF_JAVA_UTIL = LibraryRecord.of(
            "com{}google{}protobuf",
            "protobuf-java-util",
            "4.34.1",
            "protobuf-java-util",
            "XYL6dEppNb7x/OMp6IawPSbEY4nbfj9c2DY7x5WQhYA="
    ),
    GUAVA = LibraryRecord.of(
            "com{}google{}guava",
            "guava",
            "33.3.0-jre",
            "guava",
            "363DvOMQHv8UUqrkfXyDP+5EO0e9+e8TMRtsfKtmPd8="
    ),
    FAILUREACCESS = LibraryRecord.of(
            "com{}google{}guava",
            "failureaccess",
            "1.0.2",
            "failureaccess",
            "io+Bz5s1nj9t+mkaHndphcBh7y8iPJssgHU+G0WOgGQ="
    ),
    PROTOVALIDATE = LibraryRecord.of(
            "build{}buf",
            "protovalidate",
            "0.4.1",
            "protovalidate",
            "lRnCtcdIF3UXZRI+8uNkySJDND73FGtI22KVdVETflo="
    ),
    CEL_CORE = LibraryRecord.of(
            "org{}projectnessie{}cel",
            "cel-core",
            "0.5.1",
            "cel-core",
            "0o8FHajmp+f6Em4zDOJJtU3dUg90jstAS9ig4cdJ7Jw="
    ),
    CEL_GENERATED_ANTLR = LibraryRecord.of(
            "org{}projectnessie{}cel",
            "cel-generated-antlr",
            "0.5.1",
            "cel-generated-antlr",
            "+JaYayVfLH39tP0baqHXknbxTswHl9U4d8LvErPsLT0="
    ),
    CEL_GENERATED_PB = LibraryRecord.of(
            "org{}projectnessie{}cel",
            "cel-generated-pb",
            "0.5.1",
            "cel-generated-pb",
            "phr8IIfDLRyjUCBSXQ0yB5tQtG1MfnFrb/KhZfz91gI="
    ),
    AGRONA = LibraryRecord.of(
            "org{}agrona",
            "agrona",
            "1.22.0",
            "agrona",
            "1CKlfonMtq4PlILvoW9d0LmT2sDbOGD1SwM/IfRSBs8="
    ),
    IPADDRESS = LibraryRecord.of(
            "com{}github{}seancfoley",
            "ipaddress",
            "5.5.1",
            "ipaddress",
            "XqReV9sMLWJBkqUEbO6kGwrK95t2ilShTIZ5G7ZFknA="
    ),
    JAKARTA_MAIL_API = LibraryRecord.of(
            "jakarta{}mail",
            "jakarta.mail-api",
            "2.1.3",
            "jakarta-mail-api",
            "gFG1jXX5gvmluWOzdlQm6CSypkhl7wrxcgXkVbmNsFw="
    ),
    JAKARTA_ACTIVATION_API = LibraryRecord.of(
            "jakarta{}activation",
            "jakarta.activation-api",
            "2.1.3",
            "jakarta-activation-api",
            "AbF21xihaSY+eCkGkfxHmXcYa8xrMzSHMlCE1lhvRic="
    ),
    MODL_PROTO = LibraryRecord.of(
            "gg{}modl",
            "proto",
            "1.0.0",
            "proto",
            "cSZqGppxeHNsQSCBRgobOtanIk+s5tfD2oRuxzamYVY="
    );

    public static final List<LibraryRecord> COMMON = Arrays.asList(
            SNAKEYAML,
            GSON,
            HTTPCLIENT5,
            HTTPCORE5,
            HTTPCORE5_H2,
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
