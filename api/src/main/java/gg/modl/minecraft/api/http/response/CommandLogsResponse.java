package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CommandLogsResponse {
    private List<CommandLogEntry> entries;

    @Data
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandLogEntry {
        private String uuid;
        private String username;
        private String command;
        private long timestamp;
        private String server;
    }
}
