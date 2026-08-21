package gg.modl.minecraft.core.login;

import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.support.MapLocaleManager;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LoginServiceTest {

    private final LoginService loginService = new LoginService(
            new MapLocaleManager()
                    .put("api_errors.ban_check_failed", "ban check failed")
                    .put("api_errors.connection_failed", "connection failed"),
            null, null, null, null, null, null);

    @Test
    void panelUnavailableFailsClosed() {
        LoginService.LoginResult result = loginService.handleLoginError(
                new ExecutionException(new PanelUnavailableException("http://x", 503, "down")));
        assertInstanceOf(LoginService.LoginResult.Denied.class, result);
    }

    @Test
    void wrappedSocketTimeoutFailsClosedViaDefault() {
        LoginService.LoginResult result = loginService.handleLoginError(
                new ExecutionException(new RuntimeException("V3 HTTP request failed",
                        new SocketTimeoutException("Read timed out"))));
        assertInstanceOf(LoginService.LoginResult.Denied.class, result);
    }

    @Test
    void directSocketTimeoutFailsClosedViaInterruptedIoBranch() {
        LoginService.LoginResult result = loginService.handleLoginError(
                new ExecutionException(new SocketTimeoutException("Read timed out")));
        assertInstanceOf(LoginService.LoginResult.Denied.class, result);
    }

    @Test
    void genericFailureFailsClosedViaDefault() {
        LoginService.LoginResult result = loginService.handleLoginError(
                new ExecutionException(new RuntimeException("boom")));
        assertInstanceOf(LoginService.LoginResult.Denied.class, result);
    }

    @Test
    void timeoutExceptionFailsClosed() {
        LoginService.LoginResult result = loginService.handleLoginError(new TimeoutException());
        assertInstanceOf(LoginService.LoginResult.Denied.class, result);
    }
}
