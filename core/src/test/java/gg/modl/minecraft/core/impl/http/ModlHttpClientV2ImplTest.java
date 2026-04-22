package gg.modl.minecraft.core.impl.http;

import com.google.gson.Gson;
import gg.modl.minecraft.api.http.response.PlayerProfileResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.api.http.response.TicketsResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private Gson extractGson() throws Exception {
        ModlHttpClientV2Impl client = new ModlHttpClientV2Impl("http://localhost", "api-key", "example.com", false);
        Field field = ModlHttpClientV2Impl.class.getDeclaredField("gson");
        field.setAccessible(true);
        return (Gson) field.get(client);
    }
}
