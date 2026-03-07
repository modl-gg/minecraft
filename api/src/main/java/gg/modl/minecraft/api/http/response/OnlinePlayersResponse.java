package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class OnlinePlayersResponse {
    private List<OnlinePlayer> players;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OnlinePlayer {
        private String uuid, username;
        private Date joinedAt;
        private Long totalPlaytimeMs;
    }
}
