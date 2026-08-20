package gg.modl.minecraft.core.login;

import gg.modl.minecraft.core.support.MapLocaleManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxyLoginFlowTest {
    private static final long TIMEOUT_SECONDS = 5;

    private static final String BAN_CHECK_FAILED = "Unable to verify ban status.";

    private final LoginService loginService = new LoginService(
            new MapLocaleManager().put("api_errors.ban_check_failed", BAN_CHECK_FAILED),
            null, null, null, null, null, null);

    @Test
    void collaboratorFailureDeniesInsteadOfCompletingExceptionally() throws Exception {
        ProxyLoginFlow flow = new ProxyLoginFlow(null, null, loginService, null, null, TIMEOUT_SECONDS);

        CompletableFuture<LoginService.LoginResult> verdict =
                flow.begin(UUID.randomUUID(), "Notch", "203.0.113.7", "lobby");

        assertFalse(verdict.isCompletedExceptionally());
        assertInstanceOf(LoginService.LoginResult.Denied.class, verdict.get(1, TimeUnit.SECONDS));
    }

    @Test
    void deniedResultAlwaysYieldsANonNullMessage() {
        String message = loginService.denialMessage(new LoginService.LoginResult.Denied(null));

        assertNotNull(message);
        assertEquals(BAN_CHECK_FAILED, message);
    }

    @Test
    void unknownResultDeniesByDefault() {
        assertEquals(BAN_CHECK_FAILED, loginService.denialMessage(null));
    }

    @Test
    void allowedResultYieldsNoDenialMessage() {
        assertNull(loginService.denialMessage(new LoginService.LoginResult.Allowed(null)));
    }
}
