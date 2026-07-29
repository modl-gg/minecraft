package gg.modl.minecraft.api.http;

import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.api.http.request.NotificationAcknowledgeRequest;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PlayerGetRequest;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.request.PlayerNameRequest;
import gg.modl.minecraft.api.http.response.LinkedAccountsResponse;
import gg.modl.minecraft.api.http.response.OnlinePlayersResponse;
import gg.modl.minecraft.api.http.response.PaginatedNotesResponse;
import gg.modl.minecraft.api.http.response.PlayerGetResponse;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.PlayerLookupResponse;
import gg.modl.minecraft.api.http.response.PlayerNameResponse;
import gg.modl.minecraft.api.http.response.PlayerNoteCreateResponse;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerApi {

    @NotNull CompletableFuture<PlayerProfileResponse> getPlayerProfile(@NotNull UUID uuid);

    @NotNull CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid);

    @NotNull CompletableFuture<LinkedAccountsResponse> getLinkedAccounts(@NotNull UUID uuid, int page, int limit);

    @NotNull CompletableFuture<PlayerLoginResponse> playerLogin(@NotNull PlayerLoginRequest request);

    @NotNull CompletableFuture<Void> playerDisconnect(@NotNull PlayerDisconnectRequest request);

    @NotNull CompletableFuture<PlayerGetResponse> getPlayer(@NotNull PlayerGetRequest request);

    @NotNull CompletableFuture<PlayerNameResponse> getPlayer(@NotNull PlayerNameRequest request);

    @NotNull CompletableFuture<PlayerLookupResponse> lookupPlayer(@NotNull PlayerLookupRequest request);

    @NotNull CompletableFuture<PlayerProfileResponse> lookupPlayerProfile(@NotNull PlayerLookupRequest request);

    @NotNull CompletableFuture<Void> createPlayerNote(@NotNull CreatePlayerNoteRequest request);

    @NotNull CompletableFuture<PlayerNoteCreateResponse> createPlayerNoteWithResponse(@NotNull CreatePlayerNoteRequest request);

    @NotNull CompletableFuture<PaginatedNotesResponse> getPlayerNotes(@NotNull UUID uuid, int page, int limit);

    @NotNull CompletableFuture<OnlinePlayersResponse> getOnlinePlayers();

    @NotNull CompletableFuture<Void> updatePlayerServer(@NotNull String minecraftUuid, @NotNull String serverName);

    @NotNull CompletableFuture<Void> submitIpInfo(@NotNull String minecraftUUID, @NotNull String ip,
                                          String country, String region, String asn, boolean proxy, boolean hosting);

    @NotNull CompletableFuture<Void> acknowledgeNotifications(@NotNull NotificationAcknowledgeRequest request);
}
