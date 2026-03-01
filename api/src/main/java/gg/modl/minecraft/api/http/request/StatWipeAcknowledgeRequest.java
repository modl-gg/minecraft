package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatWipeAcknowledgeRequest {
    @NotNull
    private String punishmentId;

    @Nullable
    private String serverName;

    private boolean success;
}
