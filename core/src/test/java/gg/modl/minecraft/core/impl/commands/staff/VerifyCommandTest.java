package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.Staff2faTokenResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.Staff2faService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import revxrsal.commands.command.CommandActor;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VerifyCommandTest {
    @Test
    void sends_verify_link_and_replies_on_platform_main_thread() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174200");
        CompletableFuture<Staff2faTokenResponse> tokenFuture = new CompletableFuture<>();
        TestPlatform platform = new TestPlatform(new AbstractPlayer(senderUuid, "ModlStaff", "203.0.113.10", true));
        TestLocaleManager localeManager = new TestLocaleManager();
        TestStaff2faService staff2faService = new TestStaff2faService(true, false);
        HttpClientHolder httpClientHolder = new HttpClientHolder(httpClientReturning(tokenFuture));
        VerifyCommand command = new VerifyCommand(platform.platform(), localeManager, staff2faService, httpClientHolder);
        TestActor actor = new TestActor(senderUuid);

        command.verify(actor.commandActor());

        Staff2faTokenResponse response = new Staff2faTokenResponse();
        response.setVerifyUrl("https://modl.gg/verify/token-123");
        tokenFuture.complete(response);

        assertEquals(Collections.emptyList(), actor.replies());
        assertNull(platform.sentJson());

        platform.runScheduledTask();

        assertEquals(listOf(
                "Verify your identity",
                "Open this link to continue",
                "Complete verification in your browser"
        ), actor.replies());
        assertEquals(senderUuid, platform.sentJsonUuid());
        assertEquals(
                "{\"text\":\"Click here\",\"color\":\"green\",\"bold\":true,"
                        + "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://modl.gg/verify/token-123\"},"
                        + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Click to open verification page\"}}",
                platform.sentJson()
        );
    }

    @Test
    void sends_valid_verify_link_json_for_urls_that_need_json_escaping() {
        UUID senderUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174201");
        CompletableFuture<Staff2faTokenResponse> tokenFuture = new CompletableFuture<>();
        TestPlatform platform = new TestPlatform(new AbstractPlayer(senderUuid, "ModlStaff", "203.0.113.11", true));
        TestLocaleManager localeManager = new TestLocaleManager();
        localeManager.messages.put("verify.click_here", "Click \"here\"\\now");
        TestStaff2faService staff2faService = new TestStaff2faService(true, false);
        HttpClientHolder httpClientHolder = new HttpClientHolder(httpClientReturning(tokenFuture, senderUuid, "203.0.113.11"));
        VerifyCommand command = new VerifyCommand(platform.platform(), localeManager, staff2faService, httpClientHolder);
        TestActor actor = new TestActor(senderUuid);

        command.verify(actor.commandActor());

        Staff2faTokenResponse response = new Staff2faTokenResponse();
        response.setVerifyUrl("https://modl.gg/verify/token\"\\line\nnext");
        tokenFuture.complete(response);
        platform.runScheduledTask();

        JsonObject json = JsonParser.parseString(platform.sentJson()).getAsJsonObject();
        assertEquals("Click \"here\"\\now", json.get("text").getAsString());
        assertEquals("open_url", json.getAsJsonObject("clickEvent").get("action").getAsString());
        assertEquals("https://modl.gg/verify/token\"\\line\nnext",
                json.getAsJsonObject("clickEvent").get("value").getAsString());
        assertEquals("Click to open verification page",
                json.getAsJsonObject("hoverEvent").get("contents").getAsString());
    }

    private static ModlHttpClient httpClientReturning(CompletableFuture<Staff2faTokenResponse> tokenFuture) {
        return httpClientReturning(tokenFuture, UUID.fromString("123e4567-e89b-12d3-a456-426614174200"), "203.0.113.10");
    }

    private static ModlHttpClient httpClientReturning(CompletableFuture<Staff2faTokenResponse> tokenFuture,
                                                       UUID expectedUuid,
                                                       String expectedIpAddress) {
        return (ModlHttpClient) Proxy.newProxyInstance(
                ModlHttpClient.class.getClassLoader(),
                new Class<?>[] {ModlHttpClient.class},
                (proxy, method, args) -> {
                    if ("generateStaff2faToken".equals(method.getName())) {
                        assertEquals(expectedUuid.toString(), args[0]);
                        assertEquals(expectedIpAddress, args[1]);
                        return tokenFuture;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static List<String> listOf(String first, String second, String third) {
        List<String> result = new ArrayList<>();
        result.add(first);
        result.add(second);
        result.add(third);
        return result;
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

    private static class TestLocaleManager extends LocaleManager {
        private final Map<String, String> messages = new HashMap<>();

        private TestLocaleManager() {
            messages.put("verify.header", "Verify your identity");
            messages.put("verify.instructions", "Open this link to continue");
            messages.put("verify.click_here", "Click here");
            messages.put("verify.footer", "Complete verification in your browser");
        }

        @Override
        public String getMessage(String path) {
            return messages.get(path);
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

    private static class TestPlatform {
        private final AbstractPlayer player;
        private final AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        private UUID sentJsonUuid;
        private String sentJson;

        private TestPlatform(AbstractPlayer player) {
            this.player = player;
        }

        private Platform platform() {
            return (Platform) Proxy.newProxyInstance(
                    Platform.class.getClassLoader(),
                    new Class<?>[] {Platform.class},
                    (proxy, method, args) -> {
                        if ("sendJsonMessage".equals(method.getName())) {
                            sentJsonUuid = (UUID) args[0];
                            sentJson = (String) args[1];
                            return null;
                        }
                        if ("getPlayer".equals(method.getName())) return player;
                        if ("runOnMainThread".equals(method.getName())) {
                            scheduledTask.set((Runnable) args[0]);
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private void runScheduledTask() {
            Runnable task = scheduledTask.get();
            task.run();
        }

        private UUID sentJsonUuid() {
            return sentJsonUuid;
        }

        private String sentJson() {
            return sentJson;
        }
    }
}
