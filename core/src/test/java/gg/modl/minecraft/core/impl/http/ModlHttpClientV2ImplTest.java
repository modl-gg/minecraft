package gg.modl.minecraft.core.impl.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.modl.minecraft.api.http.ChatLogEntry;
import gg.modl.minecraft.api.http.CommandLogEntry;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModlHttpClientV2ImplTest {
    @Test
    void gsonParsesIsoTimestampsInPlayerProfileResponses() throws Exception {
        Gson gson = extractGson();

        PlayerProfileResponse response = gson.fromJson(
                "{"
                        + "\"status\":200,"
                        + "\"profile\":{"
                        + "\"_id\":\"player-1\","
                        + "\"minecraftUuid\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"usernames\":[{\"username\":\"modltarget\",\"date\":\"2026-04-21T01:36:48.919Z\"}]"
                        + "}"
                        + "}",
                PlayerProfileResponse.class
        );

        assertEquals(1776735408919L, response.getProfile().getUsernames().get(0).getDate().getTime());
    }

    @Test
    void gsonParsesIsoTimestampsInReportsAndTicketsResponses() throws Exception {
        Gson gson = extractGson();

        ReportsResponse reports = gson.fromJson(
                "{"
                        + "\"status\":200,"
                        + "\"reports\":[{"
                        + "\"id\":\"REPORT-1\","
                        + "\"createdAt\":\"2026-04-21T19:50:01.907Z\""
                        + "}]"
                        + "}",
                ReportsResponse.class
        );
        TicketsResponse tickets = gson.fromJson(
                "{"
                        + "\"status\":200,"
                        + "\"tickets\":[{"
                        + "\"id\":\"TICKET-1\","
                        + "\"createdAt\":\"2026-04-21T19:50:01.907Z\","
                        + "\"updatedAt\":\"2026-04-21T19:55:01.907Z\""
                        + "}]"
                        + "}",
                TicketsResponse.class
        );

        assertEquals(1776801001907L, reports.getReports().get(0).getCreatedAt().getTime());
        assertEquals(1776801001907L, tickets.getTickets().get(0).getCreatedAt().getTime());
        assertEquals(1776801301907L, tickets.getTickets().get(0).getUpdatedAt().getTime());
    }

    @Test
    void gsonSerializesSyncChatAndCommandLogs() throws Exception {
        Gson gson = extractGson();
        SyncRequest request = new SyncRequest(
                "2026-05-18T00:00:00.000Z",
                Collections.singletonList(new SyncRequest.OnlinePlayer("player-uuid", "modlplayer", "127.0.0.1", 1234L)),
                "hub",
                "hub-1",
                Collections.singletonList(new ChatLogEntry("player-uuid", "modlplayer", "hello", "hub", 1779062400000L)),
                Collections.singletonList(new CommandLogEntry("player-uuid", "modlplayer", "/spawn", "hub", 1779062401000L)),
                null
        );

        JsonObject json = gson.toJsonTree(request).getAsJsonObject();

        assertTrue(json.has("chatLogs"));
        assertEquals("hello", json.getAsJsonArray("chatLogs").get(0).getAsJsonObject().get("message").getAsString());
        assertTrue(json.has("commandLogs"));
        assertEquals("/spawn", json.getAsJsonArray("commandLogs").get(0).getAsJsonObject().get("command").getAsString());
    }

    @Test
    void shutdownStopsHttpExecutor() throws Exception {
        ModlHttpClientV2Impl client = new ModlHttpClientV2Impl("http://localhost", "api-key", "example.com", false);
        Field field = findField(ModlHttpClientV2Impl.class, "executor");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(client);

        client.shutdown();

        assertTrue(executor.isShutdown());
    }

    @Test
    void newHttpExecutorThreadsAreDaemonAndNamed() throws Exception {
        ModlHttpClientV2Impl client = new ModlHttpClientV2Impl("http://localhost", "api-key", "example.com", false);
        Field field = findField(ModlHttpClientV2Impl.class, "executor");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(client);

        Thread thread = executor.getThreadFactory().newThread(() -> {});

        assertTrue(thread.isDaemon());
        assertTrue(thread.getName().startsWith("modl-http-"));
        assertNotEquals("modl-http", thread.getName());
    }

    @Test
    void saturatedExecutorYieldsFailedFutureInsteadOfThrowing() throws Exception {
        ModlHttpClientV2Impl client = new ModlHttpClientV2Impl("http://localhost", "api-key", "example.com", false);
        Field field = findField(ModlHttpClientV2Impl.class, "executor");
        field.setAccessible(true);
        ((ThreadPoolExecutor) field.get(client)).shutdown();

        CompletableFuture<PunishmentTypesResponse> future = client.getPunishmentTypes();

        assertTrue(future.isCompletedExceptionally());

        Throwable[] captured = new Throwable[1];
        future.exceptionally(throwable -> {
            captured[0] = throwable;
            return null;
        }).join();
        assertTrue(captured[0] instanceof PanelUnavailableException);
    }

    private Gson extractGson() throws Exception {
        ModlHttpClientV2Impl client = new ModlHttpClientV2Impl("http://localhost", "api-key", "example.com", false);
        Field field = findField(ModlHttpClientV2Impl.class, "gson");
        field.setAccessible(true);
        return (Gson) field.get(client);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
