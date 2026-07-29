package gg.modl.minecraft.api.http;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class ChatLogEntry {
    private String uuid, username, message, server;
    private long timestamp;
}
