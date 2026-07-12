package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.response.ReportsResponse;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ReportApi {

    @NotNull CompletableFuture<ReportsResponse> getReports(String status);

    @NotNull CompletableFuture<ReportsResponse> getPlayerReports(@NotNull UUID playerUuid, String status);

    @NotNull CompletableFuture<Void> dismissReport(@NotNull String reportId, String dismissedBy, String reason);

    @NotNull CompletableFuture<Void> resolveReport(@NotNull String reportId, String resolvedBy, String resolution, String punishmentId);
}
