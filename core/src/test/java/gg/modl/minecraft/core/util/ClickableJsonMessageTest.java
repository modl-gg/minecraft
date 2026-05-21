package gg.modl.minecraft.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickableJsonMessageTest {
    @Test
    void builds_open_url_message_with_structured_escaping() {
        String url = "https://panel.modl.gg/ticket/id\"\\line\nnext?query=one two";
        String hover = "Open ticket \"id\"\\line\nnext";

        String json = ClickableJsonMessage.empty()
                .extra(ClickableJsonMessage.text("Ticket: ").color("gold"))
                .extra(ClickableJsonMessage.text("[Click]")
                        .color("aqua")
                        .underlined(true)
                        .openUrl(url)
                        .hoverText(hover))
                .toJson();

        JsonObject link = extraAt(json, 1);
        assertEquals("[Click]", link.get("text").getAsString());
        assertEquals("aqua", link.get("color").getAsString());
        assertTrue(link.get("underlined").getAsBoolean());
        assertEquals("open_url", link.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals(url, link.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("show_text", link.getAsJsonObject("hoverEvent").get("action").getAsString());
        assertEquals(hover, link.getAsJsonObject("hoverEvent").get("value").getAsString());
    }

    @Test
    void builds_run_command_message_without_pre_escaping_command_value() {
        String command = "/target Player\"\\Name\nNext";

        String json = ClickableJsonMessage.empty()
                .extra(ClickableJsonMessage.text("[Report]")
                        .color("gray")
                        .italic(true)
                        .runCommand(command)
                        .hoverText("Click\nnow"))
                .toJson();

        JsonObject link = extraAt(json, 0);
        assertEquals("run_command", link.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals(command, link.getAsJsonObject("clickEvent").get("value").getAsString());
        assertTrue(link.get("italic").getAsBoolean());
    }

    @Test
    void supports_hover_contents_for_existing_verify_message_shape() {
        String json = ClickableJsonMessage.text("Verify")
                .color("green")
                .bold(true)
                .openUrl("https://modl.gg/verify?id=abc\"123")
                .hoverContents("Click to open verification page")
                .toJson();

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("Verify", root.get("text").getAsString());
        assertEquals("green", root.get("color").getAsString());
        assertTrue(root.get("bold").getAsBoolean());
        assertEquals("https://modl.gg/verify?id=abc\"123",
                root.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("Click to open verification page",
                root.getAsJsonObject("hoverEvent").get("contents").getAsString());
    }

    private static JsonObject extraAt(String json, int index) {
        JsonArray extra = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("extra");
        return extra.get(index).getAsJsonObject();
    }
}
