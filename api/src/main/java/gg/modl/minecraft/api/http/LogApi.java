package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.request.ChatLogBatchRequest;
import gg.modl.minecraft.api.http.request.CommandLogBatchRequest;
import gg.modl.minecraft.api.http.response.ChatLogsResponse;
import gg.modl.minecraft.api.http.response.CommandLogsResponse;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface LogApi {

    @NotNull CompletableFuture<Void> submitChatLogs(@NotNull ChatLogBatchRequest request);

    @NotNull CompletableFuture<Void> submitCommandLogs(@NotNull CommandLogBatchRequest request);

    @NotNull CompletableFuture<ChatLogsResponse> getChatLogs(@NotNull String playerUuid, int limit);

    @NotNull CompletableFuture<CommandLogsResponse> getCommandLogs(@NotNull String playerUuid, int limit);
}
