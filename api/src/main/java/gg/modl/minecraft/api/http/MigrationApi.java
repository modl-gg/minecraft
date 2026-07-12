package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.request.MigrationStatusUpdateRequest;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public interface MigrationApi {

    @NotNull CompletableFuture<Void> updateMigrationStatus(@NotNull MigrationStatusUpdateRequest request);

    @NotNull CompletableFuture<Boolean> uploadMigrationFile(@NotNull File file);
}
