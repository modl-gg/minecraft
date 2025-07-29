package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PunishmentAcknowledgeRequest {
    @NotNull
    private String punishmentId;
    
    @NotNull
    private String playerUuid;
    
    @NotNull
    private String executedAt;
    
    private boolean success;
    
    @Nullable
    private String errorMessage;
}