package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.DashboardStatsResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface SyncApi {

    @NotNull CompletableFuture<SyncResponse> sync(@NotNull SyncRequest request);

    @NotNull CompletableFuture<DashboardStatsResponse> getDashboardStats();
}
