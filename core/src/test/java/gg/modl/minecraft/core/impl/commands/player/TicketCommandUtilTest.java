package gg.modl.minecraft.core.impl.commands.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.locale.LocaleManager;
import org.junit.jupiter.api.Test;
import revxrsal.commands.command.CommandActor;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketCommandUtilTest {
    @Test
    void escapes_dynamic_ticket_url_and_hover_values_in_clickable_ticket_message() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174301");
        AtomicReference<String> sentJson = new AtomicReference<>();
        TicketCommandUtil util = new TicketCommandUtil(new Cache(new CachedProfileRegistry()));
        TestLocaleManager localeManager = new TestLocaleManager();
        String message = "Complete \"Appeal\"";
        String clickText = "Open \"ticket\"\\now";
        String ticketUrl = "https://panel.modl.gg/ticket/id\"\\line\nnext?token=a b";
        String ticketId = "ticket\"\\id\nnext";
        localeManager.messages.put("messages.click_to_view", clickText);
        localeManager.messages.put("messages.click_to_view_hover", "Hover " + ticketId);

        util.sendClickableTicketMessage(actor(senderUuid), platformCapturingScheduledJson(sentJson), localeManager,
                message, ticketUrl, ticketId);

        JsonArray extra = JsonParser.parseString(sentJson.get()).getAsJsonObject().getAsJsonArray("extra");
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

    private static Platform platformCapturingScheduledJson(AtomicReference<String> sentJson) {
        return (Platform) Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) -> {
                    if ("runOnMainThread".equals(method.getName())) {
                        ((Runnable) args[0]).run();
                        return null;
                    }
                    if ("sendJsonMessage".equals(method.getName())) {
                        sentJson.set((String) args[1]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static class TestLocaleManager extends LocaleManager {
        private final Map<String, String> messages = new HashMap<>();

        @Override
        public String getMessage(String path) {
            return messages.get(path);
        }

        @Override
        public String getMessage(String path, Map<String, String> placeholders) {
            String message = messages.get(path);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return message;
        }
    }
}
