package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.Staff2faTokenResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.support.FakeModlHttpClient;
import gg.modl.minecraft.core.support.FakePlatform;
import gg.modl.minecraft.core.support.MapLocaleManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import revxrsal.commands.command.CommandActor;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VerifyCommandTest {
    @Test
    void sendsVerifyLinkAndRepliesOnPlatformMainThread() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174200");
        CompletableFuture<Staff2faTokenResponse> tokenFuture = new CompletableFuture<>();
        FakePlatform platform = platform(new AbstractPlayer(senderUuid, "ModlStaff", "203.0.113.10", true));
        MapLocaleManager localeManager = localeManager();
        TestStaff2faService staff2faService = new TestStaff2faService(true, false);
        HttpClientHolder httpClientHolder = new HttpClientHolder(httpClientReturning(tokenFuture));
        VerifyCommand command = new VerifyCommand(platform, localeManager, staff2faService, httpClientHolder);
        TestActor actor = new TestActor(senderUuid);

        command.verify(actor.commandActor());

        Staff2faTokenResponse response = new Staff2faTokenResponse(null, "https://modl.gg/verify/token-123");
        tokenFuture.complete(response);

        assertEquals(Collections.emptyList(), actor.replies());
        assertNull(platform.lastJson());

        platform.runScheduledTasks();

        assertEquals(Arrays.asList(
                "Verify your identity",
                "Open this link to continue",
                "Complete verification in your browser"
        ), actor.replies());
        assertEquals(senderUuid, platform.lastJsonUuid());
        assertEquals(
                "{\"text\":\"Click here\",\"color\":\"green\",\"bold\":true,"
                        + "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://modl.gg/verify/token-123\"},"
                        + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Click to open verification page\"}}",
                platform.lastJson()
        );
    }

    @Test
    void sendsValidVerifyLinkJsonForUrlsThatNeedJsonEscaping() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174201");
        CompletableFuture<Staff2faTokenResponse> tokenFuture = new CompletableFuture<>();
        FakePlatform platform = platform(new AbstractPlayer(senderUuid, "ModlStaff", "203.0.113.11", true));
        MapLocaleManager localeManager = localeManager();
        localeManager.put("verify.click_here", "Click \"here\"\\now");
        TestStaff2faService staff2faService = new TestStaff2faService(true, false);
        HttpClientHolder httpClientHolder = new HttpClientHolder(httpClientReturning(tokenFuture, senderUuid, "203.0.113.11"));
        VerifyCommand command = new VerifyCommand(platform, localeManager, staff2faService, httpClientHolder);
        TestActor actor = new TestActor(senderUuid);

        command.verify(actor.commandActor());

        Staff2faTokenResponse response = new Staff2faTokenResponse(null, "https://modl.gg/verify/token\"\\line\nnext");
        tokenFuture.complete(response);
        platform.runScheduledTasks();

        JsonObject json = JsonParser.parseString(platform.lastJson()).getAsJsonObject();
        assertEquals("Click \"here\"\\now", json.get("text").getAsString());
        assertEquals("open_url", json.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals("https://modl.gg/verify/token\"\\line\nnext",
                json.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("Click to open verification page",
                json.getAsJsonObject("hoverEvent").get("contents").getAsString());
    }

    private static FakePlatform platform(AbstractPlayer player) {
        return new FakePlatform().register(player).autoRunMainThread(false);
    }

    private static MapLocaleManager localeManager() {
        return new MapLocaleManager()
                .put("verify.header", "Verify your identity")
                .put("verify.instructions", "Open this link to continue")
                .put("verify.click_here", "Click here")
                .put("verify.footer", "Complete verification in your browser");
    }

    private static ModlHttpClient httpClientReturning(CompletableFuture<Staff2faTokenResponse> tokenFuture) {
        return httpClientReturning(tokenFuture, UUID.fromString("123e4567-e89b-12d3-a456-426614174200"), "203.0.113.10");
    }

    private static ModlHttpClient httpClientReturning(CompletableFuture<Staff2faTokenResponse> tokenFuture,
                                                      UUID expectedUuid,
                                                      String expectedIpAddress) {
        return new FakeModlHttpClient() {
            @Override
            public CompletableFuture<Staff2faTokenResponse> generateStaff2faToken(String minecraftUuid, String ip) {
                assertEquals(expectedUuid.toString(), minecraftUuid);
                assertEquals(expectedIpAddress, ip);
                return tokenFuture;
            }
        };
    }

    private static class TestActor {
        private final UUID uuid;
        private final List<String> replies = new ArrayList<>();

        private TestActor(UUID uuid) {
            this.uuid = uuid;
        }

        private CommandActor commandActor() {
            return (CommandActor) Proxy.newProxyInstance(
                    CommandActor.class.getClassLoader(),
                    new Class<?>[] {CommandActor.class},
                    (proxy, method, args) -> {
                        if ("uniqueId".equals(method.getName())) return uuid;
                        if ("reply".equals(method.getName())) {
                            replies.add((String) args[0]);
                            return null;
                        }
                        if ("name".equals(method.getName())) return "ModlStaff";
                        if ("sendRawMessage".equals(method.getName())) {
                            replies.add((String) args[0]);
                            return null;
                        }
                        if ("sendRawError".equals(method.getName())) return null;
                        if ("lamp".equals(method.getName())) return null;
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private List<String> replies() {
            return replies;
        }
    }

    private static class TestStaff2faService extends Staff2faService {
        private final boolean enabled;
        private final boolean authenticated;

        private TestStaff2faService(boolean enabled, boolean authenticated) {
            super(null, null);
            this.enabled = enabled;
            this.authenticated = authenticated;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean isAuthenticated(UUID uuid) {
            return authenticated;
        }
    }
}
