package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatLogBatchRequest {
    private List<ChatLogEntry> entries;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatLogEntry {
        private String uuid;
        private String username;
        private String message;
        private long timestamp;
        private String server;
    }
}
