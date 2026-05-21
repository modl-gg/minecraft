package gg.modl.minecraft.core.impl.commands.staff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.EvidenceUploadTokenResponse;
import gg.modl.minecraft.core.HttpClientHolder;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentActionCommandTest {
    @Test
    void escapes_dynamic_upload_token_in_clickable_upload_link() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174302");
        CompletableFuture<EvidenceUploadTokenResponse> tokenFuture = new CompletableFuture<>();
        AtomicReference<String> sentJson = new AtomicReference<>();
        String punishmentId = "punish\"\\id\nnext";
        String token = "token\"\\line\nnext";
        Platform platform = platformCapturingScheduledJson(senderUuid, sentJson);
        PunishmentActionCommand command = new PunishmentActionCommand(
                new HttpClientHolder(httpClientReturning(tokenFuture, punishmentId)),
                platform,
                new Cache(new CachedProfileRegistry()),
                new TestLocaleManager(),
                "https://panel.modl.gg"
        );

        command.punishmentAction(actor(senderUuid), "upload-evidence", punishmentId);

        EvidenceUploadTokenResponse response = new EvidenceUploadTokenResponse();
        response.setStatus(200);
        response.setToken(token);
        tokenFuture.complete(response);

        JsonObject link = JsonParser.parseString(sentJson.get()).getAsJsonObject()
                .getAsJsonArray("extra").get(0).getAsJsonObject();
        assertEquals("Click here to upload evidence", link.get("text").getAsString());
        assertEquals("open_url", link.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals("https://panel.modl.gg/upload-evidence/" + token,
                link.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("Opens in your browser", link.getAsJsonObject("hoverEvent").get("value").getAsString());
    }

    private static ModlHttpClient httpClientReturning(CompletableFuture<EvidenceUploadTokenResponse> tokenFuture,
                                                       String expectedPunishmentId) {
        return (ModlHttpClient) Proxy.newProxyInstance(
                ModlHttpClient.class.getClassLoader(),
                new Class<?>[] {ModlHttpClient.class},
                (proxy, method, args) -> {
                    if ("createEvidenceUploadToken".equals(method.getName())) {
                        assertEquals(expectedPunishmentId, args[0]);
                        return tokenFuture;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static CommandActor actor(UUID uuid) {
        return (CommandActor) Proxy.newProxyInstance(
                CommandActor.class.getClassLoader(),
                new Class<?>[] {CommandActor.class},
                (proxy, method, args) -> {
                    if ("uniqueId".equals(method.getName())) return uuid;
                    if ("reply".equals(method.getName())) return null;
                    if ("name".equals(method.getName())) return "Staff";
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Platform platformCapturingScheduledJson(UUID senderUuid, AtomicReference<String> sentJson) {
        return (Platform) Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) -> {
                    if ("getPlayer".equals(method.getName())) {
                        return new AbstractPlayer(senderUuid, "Staff", "203.0.113.20", true);
                    }
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

        private TestLocaleManager() {
            messages.put("punishment_action.generating_upload", "Generating upload link");
            messages.put("punishment_action.upload_failed", "Upload failed");
        }

        @Override
        public String getMessage(String path) {
            return messages.get(path);
        }
    }
}
