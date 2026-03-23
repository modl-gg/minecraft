package gg.modl.minecraft.core;

import gg.modl.minecraft.api.LibraryRecord;

import java.util.Arrays;
import java.util.List;

public final class Libraries {

    private Libraries() {}

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
            "com{}github{}retrooper",
            "packetevents-api",
            "2.11.2",
            "packetevents-api",
            "3iUlwXnzZ8UYPrcUG5inerI6qdtMmpvZ3M9KBvon8OM="
    ),
    PACKETEVENTS_NETTY = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-netty-common",
            "2.11.2",
            "packetevents-netty-common",
            "iFUyY8j/9ZIJGQI9KNkvS8TBuLIBLqpvt+RQALSipCM="
    ),
    PACKETEVENTS_SPIGOT = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-spigot",
            "2.11.2",
            "packetevents-spigot",
            "DNgWUMmOnz3rH65hM9MeSFYWWPwrXIU33FvsBxYD/+E="
    ),
    PACKETEVENTS_BUNGEE = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-bungeecord",
            "2.11.2",
            "packetevents-bungeecord",
            "uqfNJPYZEG8ZpvC9VXGKorGs/9e+rDd8RBsLSxOyAGc="
    ),
    PACKETEVENTS_VELOCITY = LibraryRecord.of(
            "com{}github{}retrooper",
            "packetevents-velocity",
            "2.11.2",
            "packetevents-velocity",
            "FsSHtExrXGXiXrswQIGAO46DaIZDIiVwKgzZw1BRGXA="
    ),
    ADVENTURE_NBT = LibraryRecord.of(
            "net{}kyori",
            "adventure-nbt",
            "4.25.0",
            "adventure-nbt",
            "jubaYh9JbxbGGd5uxcC5wUDOrJuJwqWzN3k429VaFi4="
    ),
    ACF_CORE = LibraryRecord.ofUrl(
            "co{}aikar",
            "acf-core",
            "0.5.1-20260118.005649-52",
            "acf-core",
            "https://repo.aikar.co/content/groups/aikar/co/aikar/acf-core/0.5.1-SNAPSHOT/acf-core-0.5.1-20260118.005649-52.jar",
            "zh32bc5eNCJzsRJTGYP2NyWhuf1JrwgqwEcZZfJ801A="
    ),
    ACF_BUKKIT = LibraryRecord.ofUrl(
            "co{}aikar",
            "acf-bukkit",
            "0.5.1-20260118.005649-52",
            "acf-bukkit",
            "https://repo.aikar.co/content/groups/aikar/co/aikar/acf-bukkit/0.5.1-SNAPSHOT/acf-bukkit-0.5.1-20260118.005649-52.jar",
            "McXMmPGrl6kbVHVxImCWCq7pJROFbqB3j0DT1vWDFFs="
    ),
    ACF_VELOCITY = LibraryRecord.ofUrl(
            "co{}aikar",
            "acf-velocity",
            "0.5.1-20260118.005649-51",
            "acf-velocity",
            "https://repo.aikar.co/content/groups/aikar/co/aikar/acf-velocity/0.5.1-SNAPSHOT/acf-velocity-0.5.1-20260118.005649-51.jar",
            "+MRdScJFX9qVtgdXYeWZA9CahltfHQUbzbppMAPVrVA="
    ),
    ACF_BUNGEE = LibraryRecord.ofUrl(
            "co{}aikar",
            "acf-bungee",
            "0.5.1-20260118.005649-52",
            "acf-bungee",
            "https://repo.aikar.co/content/groups/aikar/co/aikar/acf-bungee/0.5.1-SNAPSHOT/acf-bungee-0.5.1-20260118.005649-52.jar",
            "QC6rQuPHfRNWa/qqcM1ly9L3eKhZdGB41f0jYLBkP/s="
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
    CIRRUS_SPIGOT = LibraryRecord.ofUrl(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-spigot",
            "4.1.8",
            "cirrus-spigot",
            "https://github.com/modl-gg/minecraft-cirrus/releases/download/4.1.8-SNAPSHOT/cirrus-spigot-4.1.8-SNAPSHOT.jar",
            "FT4jxzk/+zRAunOWxgZvyuNnNt6jjLcTZPesVJO8FX4="
    ),
    CIRRUS_VELOCITY = LibraryRecord.ofUrl(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-velocity",
            "4.1.8",
            "cirrus-velocity",
            "https://github.com/modl-gg/minecraft-cirrus/releases/download/4.1.8-SNAPSHOT/cirrus-velocity-4.1.8-SNAPSHOT.jar",
            "/nHY+wjoZZoouGPYagEuQhCx8oSaBb+RHDuatatWZ+I="
    ),
    CIRRUS_BUNGEECORD = LibraryRecord.ofUrl(
            "gg{}modl{}minecraft{}cirrus",
            "cirrus-bungeecord",
            "4.1.8",
            "cirrus-bungeecord",
            "https://github.com/modl-gg/minecraft-cirrus/releases/download/4.1.8-SNAPSHOT/cirrus-bungeecord-4.1.8-SNAPSHOT.jar",
            "+E+zC1dlsL/Wv7Ihx95qK5cQNheK5nDp8rzzCDsyYhE="
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
    );

    public static final List<LibraryRecord> COMMON = Arrays.asList(
            SNAKEYAML,
            GSON,
            HTTPCLIENT5,
            HTTPCORE5,
            HTTPCORE5_H2,
            ADVENTURE_NBT
    );
}
