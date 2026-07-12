package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.core.support.TestAccounts;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static gg.modl.minecraft.core.util.Java8Collections.failedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaffProfileLookupTest {
    @Test
    void fallsBackToOnlinePlayerUuidWhenNameLookupThrows() {
        UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        PlayerProfileResponse fallbackResponse = successResponse(targetUuid, "modltarget");
        AtomicReference<String> lookupRequest = new AtomicReference<>();
        AtomicReference<UUID> profileLookupUuid = new AtomicReference<>();
        AtomicInteger onlineLookups = new AtomicInteger();

        PlayerProfileResponse response = StaffProfileLookup.lookupPlayerProfile(
                "modltarget",
                () -> {
                    lookupRequest.set("modltarget");
                    return failedFuture(new IllegalStateException("lookup by name failed"));
                },
                playerUuid -> {
                    profileLookupUuid.set(playerUuid);
                    return CompletableFuture.completedFuture(fallbackResponse);
                },
                query -> {
                    onlineLookups.incrementAndGet();
                    return new AbstractPlayer(targetUuid, query, true);
                }
        ).join();

        assertSame(fallbackResponse, response);
        assertEquals("modltarget", lookupRequest.get());
        assertEquals(targetUuid, profileLookupUuid.get());
        assertEquals(1, onlineLookups.get());
    }

    @Test
    void fallsBackToOnlinePlayerUuidWhenNameLookupReturnsNotFound() {
        UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        PlayerProfileResponse notFound = responseWithStatus(targetUuid, "modltarget", 404);
        PlayerProfileResponse fallbackResponse = successResponse(targetUuid, "modltarget");

        PlayerProfileResponse response = StaffProfileLookup.lookupPlayerProfile(
                "modltarget",
                () -> CompletableFuture.completedFuture(notFound),
                playerUuid -> {
                    assertEquals(targetUuid, playerUuid);
                    return CompletableFuture.completedFuture(fallbackResponse);
                },
                query -> new AbstractPlayer(targetUuid, query, true)
        ).join();

        assertSame(fallbackResponse, response);
    }

    @Test
    void surfacesOriginalFailureWhenOnlineFallbackIsUnavailable() {
        RuntimeException rootCause = new RuntimeException("lookup by name failed");

        CompletionException thrown = assertThrows(
                CompletionException.class,
                () -> StaffProfileLookup.lookupPlayerProfile(
                        "modltarget",
                        () -> failedFuture(rootCause),
                        playerUuid -> {
                            throw new AssertionError("uuid fallback should not run");
                        },
                        query -> null
                ).join()
        );

        assertSame(rootCause, thrown.getCause());
    }

    private static PlayerProfileResponse successResponse(UUID uuid, String username) {
        return responseWithStatus(uuid, username, 200);
    }

    private static PlayerProfileResponse responseWithStatus(UUID uuid, String username, int status) {
        return TestAccounts.profileResponse(uuid, username, status);
    }
}
