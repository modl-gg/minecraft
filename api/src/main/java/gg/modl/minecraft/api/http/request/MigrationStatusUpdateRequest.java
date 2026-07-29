package gg.modl.minecraft.api.http.request;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class MigrationStatusUpdateRequest {
    @NotNull String taskId, status, message;
    @Nullable Integer recordsProcessed, totalRecords;
}
