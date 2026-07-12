package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Getter @NoArgsConstructor @AllArgsConstructor
public class PlayerProfileResponse {
    private @NotNull Account profile;
    private int status;
    private int punishmentCount = -1;
    private int noteCount = -1;

    public int getPunishmentCount() {
        if (punishmentCount >= 0) return punishmentCount;
        return profile != null ? profile.getPunishments().size() : 0;
    }

    public int getNoteCount() {
        if (noteCount >= 0) return noteCount;
        return profile != null ? profile.getNotes().size() : 0;
    }
}
