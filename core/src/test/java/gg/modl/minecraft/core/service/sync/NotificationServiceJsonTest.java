package gg.modl.minecraft.core.service.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.support.FakePlatform;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationServiceJsonTest {
    @Test
    void staffTicketReportNotificationBuildsValidRunCommandJsonWithoutManualEscaping() {
        FakePlatform platform = new FakePlatform();
        NotificationService service = new NotificationService(
                platform,
                null,
                null,
                null,
                null,
                "https://panel.modl.gg",
                false
        );
        Map<String, Object> data = new HashMap<>();
        data.put("ticketId", "ticket\"\\id\nnext");
        data.put("subject", "Report \"subject\"\\line\nnext");
        data.put("firstReplyContent", "First reply \"quoted\"\\line\nnext");
        data.put("ticketType", "REPORT");
        data.put("category", "gameplay");
        data.put("reportedPlayer", "Target\"\\Name\nNext");
        SyncResponse.StaffNotification notification = new SyncResponse.StaffNotification(
                "notification-1",
                "TICKET_CREATED",
                "New \"ticket\"\\notice\nnext",
                data,
                1L
        );

        service.processStaffNotification(notification);

        JsonObject link = JsonParser.parseString(platform.lastStaffJsonBroadcast())
                .getAsJsonObject()
                .getAsJsonArray("extra")
                .get(0)
                .getAsJsonObject();
        assertEquals("[New \"ticket\"\\notice\nnext]", link.get("text").getAsString());
        assertEquals("run_command", link.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals("/target Target\"\\Name\nNext", link.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("Report \"subject\"\\line\nnext\n\nFirst reply \"quoted\"\\line\nnext\n\nClick to target Target\"\\Name\nNext",
                link.getAsJsonObject("hoverEvent").get("value").getAsString());
    }
}
