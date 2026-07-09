package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.http.PanelUnavailableException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LoginHandlerTest {

    @Test
    void panelUnavailableFailsClosed() {
        LoginHandler.LoginResult result = LoginHandler.handleLoginError(
                new ExecutionException(new PanelUnavailableException("http://x", 503, "down")));
        assertInstanceOf(LoginHandler.LoginResult.Denied.class, result);
    }

    @Test
    void wrappedSocketTimeoutFailsClosedViaDefault() {
        LoginHandler.LoginResult result = LoginHandler.handleLoginError(
                new ExecutionException(new RuntimeException("V3 HTTP request failed",
                        new SocketTimeoutException("Read timed out"))));
        assertInstanceOf(LoginHandler.LoginResult.Denied.class, result);
    }

    @Test
    void directSocketTimeoutFailsClosedViaInterruptedIoBranch() {
        LoginHandler.LoginResult result = LoginHandler.handleLoginError(
                new ExecutionException(new SocketTimeoutException("Read timed out")));
        assertInstanceOf(LoginHandler.LoginResult.Denied.class, result);
    }

    @Test
    void genericFailureFailsClosedViaDefault() {
        LoginHandler.LoginResult result = LoginHandler.handleLoginError(
                new ExecutionException(new RuntimeException("boom")));
        assertInstanceOf(LoginHandler.LoginResult.Denied.class, result);
    }

    @Test
    void timeoutExceptionFailsClosed() {
        LoginHandler.LoginResult result = LoginHandler.handleLoginError(new TimeoutException());
        assertInstanceOf(LoginHandler.LoginResult.Denied.class, result);
    }
}
