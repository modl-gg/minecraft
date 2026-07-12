package gg.modl.minecraft.core.impl.commands.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.MapLocaleManager;
import org.junit.jupiter.api.Test;
import revxrsal.commands.command.CommandActor;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketCommandUtilTest {
    @Test
    void escapesDynamicTicketUrlAndHoverValuesInClickableTicketMessage() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174301");
        TicketCommandUtil util = new TicketCommandUtil(new Cache(new CachedProfileRegistry()));
        String message = "Complete \"Appeal\"";
        String clickText = "Open \"ticket\"\\now";
        String ticketUrl = "https://panel.modl.gg/ticket/id\"\\line\nnext?token=a b";
        String ticketId = "ticket\"\\id\nnext";
        MapLocaleManager localeManager = new MapLocaleManager()
                .put("messages.click_to_view", clickText)
                .put("messages.click_to_view_hover", "Hover " + ticketId);
        FakePlatform platform = new FakePlatform();

        util.sendClickableTicketMessage(actor(senderUuid), platform, localeManager,
                message, ticketUrl, ticketId);

        JsonArray extra = JsonParser.parseString(platform.lastJson()).getAsJsonObject().getAsJsonArray("extra");
        JsonObject link = extra.get(2).getAsJsonObject();
        assertEquals(message + ": ", extra.get(1).getAsJsonObject().get("text").getAsString());
        assertEquals(clickText, link.get("text").getAsString());
        assertEquals("open_url", link.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals(ticketUrl, link.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("Hover " + ticketId, link.getAsJsonObject("hoverEvent").get("value").getAsString());
    }

    private static CommandActor actor(UUID uuid) {
        return (CommandActor) Proxy.newProxyInstance(
                CommandActor.class.getClassLoader(),
                new Class<?>[] {CommandActor.class},
                (proxy, method, args) -> {
                    if ("uniqueId".equals(method.getName())) return uuid;
                    if ("reply".equals(method.getName())) return null;
                    if ("name".equals(method.getName())) return "Reporter";
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
