package gg.modl.minecraft.core.punishment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.core.support.FakePlatform;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentActionMessageServiceTest {
    @Test
    void escapesDynamicPunishmentIdAndCommandPathInActionButtons() {
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174300");
        FakePlatform platform = new FakePlatform();
        String commandPath = "punishment\"action\\path";
        String punishmentId = "punish\"\\id\nnext";

        PunishmentActionMessageService service = new PunishmentActionMessageService(platform, commandPath);
        service.sendPunishmentActions(playerUuid, punishmentId);

        JsonArray extra = JsonParser.parseString(platform.lastJson()).getAsJsonObject().getAsJsonArray("extra");
        assertEquals("Punishment #" + punishmentId + ": ", extra.get(0).getAsJsonObject().get("text").getAsString());
        assertCommand(extra.get(1).getAsJsonObject(), "/" + commandPath + " modify " + punishmentId);
        assertCommand(extra.get(3).getAsJsonObject(), "/" + commandPath + " link-evidence " + punishmentId);
        assertCommand(extra.get(5).getAsJsonObject(), "/" + commandPath + " upload-evidence " + punishmentId);
    }

    private static void assertCommand(JsonObject component, String command) {
        assertEquals("run_command", component.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals(command, component.getAsJsonObject("clickEvent").get("value").getAsString());
    }
}
