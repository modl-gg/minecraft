package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class CommandLogBatchRequest {
    private List<CommandLogEntry> entries;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CommandLogEntry {
        private String uuid, username, command, server;
        private long timestamp;
    }
}
