package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class OnlinePlayersResponse extends StatusResponse {
    private List<OnlinePlayer> players;
    private int status;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class OnlinePlayer {
        private String uuid, username;
        private Date joinedAt;
        private Long totalPlaytimeMs;
    }
}
