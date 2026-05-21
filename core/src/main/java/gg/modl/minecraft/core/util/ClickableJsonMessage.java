package gg.modl.minecraft.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class ClickableJsonMessage {
    private static final Gson GSON = new Gson();

    private final JsonObject json;

    private ClickableJsonMessage(String text) {
        json = new JsonObject();
        json.addProperty("text", text);
    }

    public static ClickableJsonMessage empty() {
        return text("");
    }

    public static ClickableJsonMessage text(String text) {
        return new ClickableJsonMessage(text);
    }

    public ClickableJsonMessage extra(ClickableJsonMessage component) {
        JsonArray extra = json.has("extra") ? json.getAsJsonArray("extra") : new JsonArray();
        extra.add(component.json);
        json.add("extra", extra);
        return this;
    }

    public ClickableJsonMessage color(String color) {
        json.addProperty("color", color);
        return this;
    }

    public ClickableJsonMessage bold(boolean bold) {
        json.addProperty("bold", bold);
        return this;
    }

    public ClickableJsonMessage italic(boolean italic) {
        json.addProperty("italic", italic);
        return this;
    }

    public ClickableJsonMessage underlined(boolean underlined) {
        json.addProperty("underlined", underlined);
        return this;
    }

    public ClickableJsonMessage openUrl(String url) {
        return clickEvent("open_url", url);
    }

    public ClickableJsonMessage runCommand(String command) {
        return clickEvent("run_command", command);
    }

    public ClickableJsonMessage hoverText(String text) {
        return hoverEvent("value", text);
    }

    public ClickableJsonMessage hoverContents(String text) {
        return hoverEvent("contents", text);
    }

    public String toJson() {
        return GSON.toJson(json);
    }

    private ClickableJsonMessage clickEvent(String action, String value) {
        JsonObject clickEvent = new JsonObject();
        clickEvent.addProperty("action", action);
        clickEvent.addProperty("value", value);
        json.add("clickEvent", clickEvent);
        return this;
    }

    private ClickableJsonMessage hoverEvent(String valueKey, String text) {
        JsonObject hoverEvent = new JsonObject();
        hoverEvent.addProperty("action", "show_text");
        hoverEvent.addProperty(valueKey, text);
        json.add("hoverEvent", hoverEvent);
        return this;
    }
}
