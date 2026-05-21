package gg.modl.minecraft.core.impl.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModlHttpClientV2ImplTest {
    @Test
    void gson_parses_iso_timestamps_in_player_profile_responses() throws Exception {
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
    void gson_parses_iso_timestamps_in_reports_and_tickets_responses() throws Exception {
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
    void gson_serializes_sync_chat_and_command_logs() throws Exception {
        Gson gson = extractGson();
        SyncRequest request = new SyncRequest(
                "2026-05-18T00:00:00.000Z",
                List.of(new SyncRequest.OnlinePlayer("player-uuid", "modlplayer", "127.0.0.1", 1234L)),
                "hub",
                "hub-1",
                List.of(new SyncRequest.ChatLogEntry("player-uuid", "modlplayer", "hello", "hub", 1779062400000L)),
                List.of(new SyncRequest.CommandLogEntry("player-uuid", "modlplayer", "/spawn", "hub", 1779062401000L))
        );

        JsonObject json = gson.toJsonTree(request).getAsJsonObject();

        assertTrue(json.has("chatLogs"));
        assertEquals("hello", json.getAsJsonArray("chatLogs").get(0).getAsJsonObject().get("message").getAsString());
        assertTrue(json.has("commandLogs"));
        assertEquals("/spawn", json.getAsJsonArray("commandLogs").get(0).getAsJsonObject().get("command").getAsString());
    }

    @Test
    void shutdown_stops_http_executor() throws Exception {
        ModlHttpClientV2Impl client = new ModlHttpClientV2Impl("http://localhost", "api-key", "example.com", false);
        Field field = ModlHttpClientV2Impl.class.getDeclaredField("executor");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(client);

        Method shutdown = ModlHttpClientV2Impl.class.getDeclaredMethod("shutdown");
        shutdown.invoke(client);

        assertTrue(executor.isShutdown());
    }

    @Test
    void new_http_executor_threads_are_daemon_and_named() throws Exception {
        ModlHttpClientV2Impl client = new ModlHttpClientV2Impl("http://localhost", "api-key", "example.com", false);
        Field field = ModlHttpClientV2Impl.class.getDeclaredField("executor");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(client);

        Thread thread = executor.getThreadFactory().newThread(() -> {});

        assertTrue(thread.isDaemon());
        assertTrue(thread.getName().startsWith("modl-http-"));
        assertFalse(thread.getName().equals("modl-http"));
    }

    private Gson extractGson() throws Exception {
        ModlHttpClientV2Impl client = new ModlHttpClientV2Impl("http://localhost", "api-key", "example.com", false);
        Field field = ModlHttpClientV2Impl.class.getDeclaredField("gson");
        field.setAccessible(true);
        return (Gson) field.get(client);
    }
}
