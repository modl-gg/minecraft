package gg.modl.minecraft.core.impl.commands.staff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.EvidenceUploadTokenResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.support.FakeModlHttpClient;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.MapLocaleManager;
import org.junit.jupiter.api.Test;
import revxrsal.commands.command.CommandActor;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PunishmentActionCommandTest {
    @Test
    void escapesDynamicUploadTokenInClickableUploadLink() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174302");
        CompletableFuture<EvidenceUploadTokenResponse> tokenFuture = new CompletableFuture<>();
        String punishmentId = "punish\"\\id\nnext";
        String token = "token\"\\line\nnext";
        FakePlatform platform = new FakePlatform()
                .register(new AbstractPlayer(senderUuid, "Staff", "203.0.113.20", true));
        PunishmentActionCommand command = new PunishmentActionCommand(
                new HttpClientHolder(httpClientReturning(tokenFuture, punishmentId)),
                platform,
                new Cache(new CachedProfileRegistry()),
                localeManager(),
                "https://panel.modl.gg"
        );

        command.punishmentAction(actor(senderUuid), "upload-evidence", punishmentId);

        EvidenceUploadTokenResponse response = new EvidenceUploadTokenResponse(token, 200);
        tokenFuture.complete(response);

        JsonObject link = JsonParser.parseString(platform.lastJson()).getAsJsonObject()
                .getAsJsonArray("extra").get(0).getAsJsonObject();
        assertEquals("Click here to upload evidence", link.get("text").getAsString());
        assertEquals("open_url", link.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals("https://panel.modl.gg/upload-evidence/" + token,
                link.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("Opens in your browser", link.getAsJsonObject("hoverEvent").get("value").getAsString());
    }

    private static MapLocaleManager localeManager() {
        return new MapLocaleManager()
                .put("punishment_action.generating_upload", "Generating upload link")
                .put("punishment_action.upload_failed", "Upload failed");
    }

    private static ModlHttpClient httpClientReturning(CompletableFuture<EvidenceUploadTokenResponse> tokenFuture,
                                                      String expectedPunishmentId) {
        return new FakeModlHttpClient() {
            @Override
            public CompletableFuture<EvidenceUploadTokenResponse> createEvidenceUploadToken(String punishmentId,
                                                                                            String issuerName) {
                assertEquals(expectedPunishmentId, punishmentId);
                return tokenFuture;
            }
        };
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
}
