package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.SimplePunishment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor
public class PlayerLoginResponse {
    private int status;
    private List<SimplePunishment> activePunishments;
    private List<Map<String, Object>> pendingNotifications;
    private List<String> pendingIpLookups;
    private List<SyncResponse.PendingStatWipe> pendingStatWipes;

    public boolean hasActiveBan() {
        return activePunishments != null && activePunishments.stream().anyMatch(SimplePunishment::isBan);
    }

    public boolean hasActiveMute() {
        return activePunishments != null && activePunishments.stream().anyMatch(SimplePunishment::isMute);
    }

    public SimplePunishment getActiveBan() {
        return activePunishments != null ? activePunishments.stream()
            .filter(SimplePunishment::isBan)
            .findFirst()
            .orElse(null) : null;
    }

    public SimplePunishment getActiveMute() {
        return activePunishments != null ? activePunishments.stream()
            .filter(SimplePunishment::isMute)
            .findFirst()
            .orElse(null) : null;
    }

    public boolean hasNotifications() {
        return pendingNotifications != null && !pendingNotifications.isEmpty();
    }

    public boolean hasPendingStatWipes() {
        return pendingStatWipes != null && !pendingStatWipes.isEmpty();
    }
}
