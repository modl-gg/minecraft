package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class PlayerProfileResponse {
    private @NotNull Account profile;
    private int status;
    private int punishmentCount = -1;
    private int noteCount = -1;

    public int getPunishmentCount() {
        return punishmentCount >= 0 ? punishmentCount : profile.getPunishments().size();
    }

    public int getNoteCount() {
        return noteCount >= 0 ? noteCount : profile.getNotes().size();
    }
}
