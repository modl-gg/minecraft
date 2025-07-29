package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class Account {
    @NotNull
    @SerializedName("_id")
    private final String id;

    @NotNull
    @SerializedName("minecraftUuid")
    private final UUID minecraftUuid;

    @NotNull
    @SerializedName("usernames")
    private final List<Username> usernames;

    @NotNull
    @SerializedName("notes")
    private final List<Note> notes;

    @NotNull
    @SerializedName("ipList")
    private final List<IPAddress> ipList;

    @NotNull
    @SerializedName("punishments")
    private final List<Punishment> punishments;

    @NotNull
    @SerializedName("pendingNotifications")
    private final List<String> pendingNotifications;

    @Getter
    @RequiredArgsConstructor
    public static class Username {
        @NotNull
        @SerializedName("username")
        private final String username;

        @NotNull
        @SerializedName("date")
        private final Date date;
    }
}
