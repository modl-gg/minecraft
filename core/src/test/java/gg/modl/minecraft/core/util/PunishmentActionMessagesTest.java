package gg.modl.minecraft.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.core.Platform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentActionMessagesTest {
    @Test
    void escapes_dynamic_punishment_id_and_command_path_in_action_buttons() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174300");
        AtomicReference<String> sentJson = new AtomicReference<>();
        Platform platform = platformCapturingJson(sentJson);
        String commandPath = "punishment\"action\\path";
        String punishmentId = "punish\"\\id\nnext";

        PunishmentActionMessages.setCommandPath(commandPath);
        PunishmentActionMessages.sendPunishmentActions(platform, playerUuid, punishmentId);

        JsonArray extra = JsonParser.parseString(sentJson.get()).getAsJsonObject().getAsJsonArray("extra");
        assertEquals("Punishment #" + punishmentId + ": ", extra.get(0).getAsJsonObject().get("text").getAsString());
        assertCommand(extra.get(1).getAsJsonObject(), "/" + commandPath + " modify " + punishmentId);
        assertCommand(extra.get(3).getAsJsonObject(), "/" + commandPath + " link-evidence " + punishmentId);
        assertCommand(extra.get(5).getAsJsonObject(), "/" + commandPath + " upload-evidence " + punishmentId);
    }

    private static void assertCommand(JsonObject component, String command) {
        assertEquals("run_command", component.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals(command, component.getAsJsonObject("clickEvent").get("value").getAsString());
    }

    private static Platform platformCapturingJson(AtomicReference<String> sentJson) {
        return (Platform) Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) -> {
                    if ("sendJsonMessage".equals(method.getName())) {
                        sentJson.set((String) args[1]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
