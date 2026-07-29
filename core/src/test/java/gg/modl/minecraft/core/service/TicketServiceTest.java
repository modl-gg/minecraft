package gg.modl.minecraft.core.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.support.FakeCommandActor;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.MapLocaleManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketServiceTest {
    @Test
    void escapesDynamicTicketUrlAndHoverValuesInClickableTicketMessage() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174301");
        String message = "Complete \"Appeal\"";
        String clickText = "Open \"ticket\"\\now";
        String ticketUrl = "https://panel.modl.gg/ticket/id\"\\line\nnext?token=a b";
        String ticketId = "ticket\"\\id\nnext";
        MapLocaleManager localeManager = new MapLocaleManager()
                .put("messages.click_to_view", clickText)
                .put("messages.click_to_view_hover", "Hover " + ticketId);
        FakePlatform platform = new FakePlatform();
        TicketService service = new TicketService(new Cache(new CachedProfileRegistry()), null, platform, localeManager, "");

        service.sendClickableTicketMessage(new FakeCommandActor(senderUuid, "Reporter"), message, ticketUrl, ticketId);

        JsonArray extra = JsonParser.parseString(platform.lastJson()).getAsJsonObject().getAsJsonArray("extra");
        JsonObject link = extra.get(2).getAsJsonObject();
        assertEquals(message + ": ", extra.get(1).getAsJsonObject().get("text").getAsString());
        assertEquals(clickText, link.get("text").getAsString());
        assertEquals("open_url", link.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals(ticketUrl, link.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("Hover " + ticketId, link.getAsJsonObject("hoverEvent").get("value").getAsString());
    }
}
