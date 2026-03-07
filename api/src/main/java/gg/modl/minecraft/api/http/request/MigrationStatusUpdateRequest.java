package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class MigrationStatusUpdateRequest {
    private @NotNull final String taskId, status, message;
    private @Nullable final Integer recordsProcessed, totalRecords;
}
