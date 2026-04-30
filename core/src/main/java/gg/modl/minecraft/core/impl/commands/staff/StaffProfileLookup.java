package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.core.Platform;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

import static gg.modl.minecraft.core.util.Java8Collections.failedFuture;

final class StaffProfileLookup {
    private StaffProfileLookup() {}

    static CompletableFuture<PlayerProfileResponse> lookupPlayerProfile(ModlHttpClient httpClient, Platform platform, String playerQuery) {
        return lookupPlayerProfile(
                playerQuery,
                () -> httpClient.lookupPlayerProfile(new PlayerLookupRequest(playerQuery)),
                httpClient::getPlayerProfile,
                query -> platform.getAbstractPlayer(query, false)
        );
    }

    static CompletableFuture<PlayerProfileResponse> lookupPlayerProfile(
            String playerQuery,
            Supplier<CompletableFuture<PlayerProfileResponse>> nameLookup,
            Function<UUID, CompletableFuture<PlayerProfileResponse>> uuidLookup,
            Function<String, AbstractPlayer> onlinePlayerLookup
    ) {
        return nameLookup.get().handle((response, throwable) -> new LookupAttempt(response, unwrap(throwable)))
                .thenCompose(attempt -> {
                    if (attempt.response != null && attempt.response.getStatus() == 200) {
                        return CompletableFuture.completedFuture(attempt.response);
                    }

                    AbstractPlayer onlinePlayer = onlinePlayerLookup.apply(playerQuery);
                    if (onlinePlayer == null || onlinePlayer.getUuid() == null) {
                        if (attempt.throwable != null) {
                            return failedFuture(attempt.throwable);
                        }
                        return CompletableFuture.completedFuture(attempt.response);
                    }

                    return uuidLookup.apply(onlinePlayer.getUuid()).handle((fallbackResponse, fallbackThrowable) -> {
                        Throwable unwrappedFallback = unwrap(fallbackThrowable);
                        if (unwrappedFallback != null) {
                            if (attempt.throwable != null) {
                                throw new CompletionException(attempt.throwable);
                            }
                            throw new CompletionException(unwrappedFallback);
                        }
                        return fallbackResponse;
                    });
                });
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    private static final class LookupAttempt {
        private final PlayerProfileResponse response;
        private final Throwable throwable;

        private LookupAttempt(PlayerProfileResponse response, Throwable throwable) {
            this.response = response;
            this.throwable = throwable;
        }
    }
}
