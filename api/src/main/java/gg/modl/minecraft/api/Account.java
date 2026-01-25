package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    @SerializedName("_id")
    private String id;

    @SerializedName("minecraftUuid")
    private UUID minecraftUuid;

    @SerializedName("usernames")
    private List<Username> usernames;

    @SerializedName("notes")
    private List<Note> notes;

    @SerializedName("ipList")
    private List<IPAddress> ipList;

    @SerializedName("punishments")
    private List<Punishment> punishments;

    @SerializedName("pendingNotifications")
    private List<String> pendingNotifications;

    // Null-safe getters for list fields
    @NotNull
    public List<Username> getUsernames() {
        return usernames != null ? usernames : Collections.emptyList();
    }

    @NotNull
    public List<Note> getNotes() {
        return notes != null ? notes : Collections.emptyList();
    }

    @NotNull
    public List<IPAddress> getIpList() {
        return ipList != null ? ipList : Collections.emptyList();
    }

    @NotNull
    public List<Punishment> getPunishments() {
        return punishments != null ? punishments : Collections.emptyList();
    }

    @NotNull
    public List<String> getPendingNotifications() {
        return pendingNotifications != null ? pendingNotifications : Collections.emptyList();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Username {
        @SerializedName("username")
        private String username;

        @SerializedName("date")
        private Date date;
    }
}
