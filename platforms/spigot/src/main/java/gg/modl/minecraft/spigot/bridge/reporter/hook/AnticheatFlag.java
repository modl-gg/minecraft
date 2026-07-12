package gg.modl.minecraft.spigot.bridge.reporter.hook;

import lombok.Value;

import java.util.UUID;

@Value
public class AnticheatFlag {
    UUID uuid;
    String playerName;
    String checkName;
    String verbose;
}
