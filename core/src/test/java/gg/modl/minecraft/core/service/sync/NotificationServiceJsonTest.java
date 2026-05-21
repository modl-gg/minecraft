package gg.modl.minecraft.core.service.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.Platform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationServiceJsonTest {
    @Test
    void staff_ticket_report_notification_builds_valid_run_command_json_without_manual_escaping() {
        AtomicReference<String> broadcastJson = new AtomicReference<>();
        NotificationService service = new NotificationService(
                platformCapturingStaffJson(broadcastJson),
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

        JsonObject link = JsonParser.parseString(broadcastJson.get())
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

    private static Platform platformCapturingStaffJson(AtomicReference<String> broadcastJson) {
        return (Platform) Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) -> {
                    if ("staffJsonBroadcast".equals(method.getName())) {
                        broadcastJson.set((String) args[0]);
                        return null;
                    }
                    if ("staffBroadcast".equals(method.getName())) return null;
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
