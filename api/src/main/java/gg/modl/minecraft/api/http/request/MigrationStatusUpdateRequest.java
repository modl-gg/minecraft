package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class MigrationStatusUpdateRequest {
    @NotNull
    private final String taskId;
    @NotNull
    private final String status;
    @NotNull
    private final String message;
    @Nullable
    private final Integer recordsProcessed;
    @Nullable
    private final Integer totalRecords;
}
