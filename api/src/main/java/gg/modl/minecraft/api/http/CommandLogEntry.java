package gg.modl.minecraft.api.http;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class CommandLogEntry {
    private String uuid, username, command, server;
    private long timestamp;
}
