package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class StartupResponse {
    private String panelUrl;
    private String timestamp;
    private String serverInstanceId;
    private Boolean realtimeEnabled;
    private String realtimeUrl;
    private Integer realtimeProtocolVersion;
    private List<String> realtimeTopics;
}
