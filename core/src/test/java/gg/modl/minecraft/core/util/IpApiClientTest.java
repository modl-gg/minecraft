package gg.modl.minecraft.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpApiClientTest {
    private static final String SUCCESS_BODY = "{\"ip\":\"8.8.8.8\",\"success\":true,\"country_code\":\"US\",\"region\":\"California\",\"city\":\"Mountain View\",\"connection\":{\"isp\":\"Google LLC\"},\"security\":{\"proxy\":false,\"hosting\":true}}";
    private static final CapturingUrlHandler CAPTURING_URL_HANDLER = new CapturingUrlHandler();

    static {
        try {
            URL.setURLStreamHandlerFactory(new CapturingUrlHandlerFactory());
        } catch (Error ignored) {
        }
    }

    @BeforeEach
    void resetState() {
        CAPTURING_URL_HANDLER.reset();
        IpApiClient.initialize(IpApiClient.DEFAULT_ENABLED, IpApiClient.DEFAULT_URL_TEMPLATE);
    }

    @AfterEach
    void restoreDefaults() {
        IpApiClient.initialize(IpApiClient.DEFAULT_ENABLED, IpApiClient.DEFAULT_URL_TEMPLATE);
    }

    @Test
    void lookup_executor_threads_are_bounded_daemon_and_named() throws Exception {
        Field field = IpApiClient.class.getDeclaredField("LOOKUP_EXECUTOR");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(null);

        Thread thread = executor.getThreadFactory().newThread(() -> {});

        assertTrue(thread.isDaemon());
        assertTrue(thread.getName().startsWith("modl-ip-api-"));
        assertTrue(executor.getMaximumPoolSize() <= 2);
        assertFalse(executor.getQueue().remainingCapacity() == Integer.MAX_VALUE);
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, executor.getRejectedExecutionHandler());
    }

    @Test
    void public_ip_is_looked_up_via_https_provider_by_default() throws Exception {
        CAPTURING_URL_HANDLER.setResponse(HttpURLConnection.HTTP_OK, SUCCESS_BODY);

        Map<String, Object> ipInfo = IpApiClient.getIpInfo("8.8.8.8").get(2, TimeUnit.SECONDS);

        assertNotNull(ipInfo);
        assertEquals(1, CAPTURING_URL_HANDLER.openCount());
        assertEquals("success", ipInfo.get("status"));
        assertEquals("US", ipInfo.get("countryCode"));
        assertEquals("California", ipInfo.get("regionName"));
        assertEquals("Google LLC", ipInfo.get("as"));
        assertEquals(Boolean.FALSE, ipInfo.get("proxy"));
        assertEquals(Boolean.TRUE, ipInfo.get("hosting"));
    }

    @Test
    void legacy_is_prefixed_security_keys_are_honored() throws Exception {
        CAPTURING_URL_HANDLER.setResponse(HttpURLConnection.HTTP_OK,
                "{\"ip\":\"8.8.8.8\",\"success\":true,\"country_code\":\"US\",\"connection\":{\"isp\":\"Google LLC\"},\"security\":{\"is_proxy\":true,\"is_hosting\":true}}");

        Map<String, Object> ipInfo = IpApiClient.getIpInfo("8.8.8.8").get(2, TimeUnit.SECONDS);

        assertNotNull(ipInfo);
        assertEquals(Boolean.TRUE, ipInfo.get("proxy"));
        assertEquals(Boolean.TRUE, ipInfo.get("hosting"));
    }

    @Test
    void malformed_ip_addresses_return_local_info_without_external_lookup() throws Exception {
        Map<String, Object> ipInfo = IpApiClient.getIpInfo("999.999.999.999").get(1, TimeUnit.SECONDS);

        assertEquals("success", ipInfo.get("status"));
        assertEquals("XX", ipInfo.get("countryCode"));
        assertEquals(0, CAPTURING_URL_HANDLER.openCount());
    }

    @Test
    void private_and_link_local_addresses_return_local_info_without_external_lookup() throws Exception {
        Map<String, Object> ipInfo = IpApiClient.getIpInfo("169.254.10.20").get(1, TimeUnit.SECONDS);

        assertEquals("success", ipInfo.get("status"));
        assertEquals("XX", ipInfo.get("countryCode"));
        assertEquals(0, CAPTURING_URL_HANDLER.openCount());
    }

    @Test
    void disabled_lookup_returns_null_for_public_ip_without_opening_connection() throws Exception {
        IpApiClient.initialize(false, IpApiClient.DEFAULT_URL_TEMPLATE);

        Map<String, Object> ipInfo = IpApiClient.getIpInfo("8.8.8.8").get(1, TimeUnit.SECONDS);

        assertNull(ipInfo);
        assertEquals(0, CAPTURING_URL_HANDLER.openCount());
    }

    @Test
    void plaintext_http_scheme_is_refused_even_if_configured() throws Exception {
        IpApiClient.initialize(true, "http://example.com/{ip}");

        Map<String, Object> ipInfo = IpApiClient.getIpInfo("8.8.8.8").get(1, TimeUnit.SECONDS);

        assertNull(ipInfo);
        assertEquals(0, CAPTURING_URL_HANDLER.openCount());
    }

    @Test
    void server_error_responses_return_null_without_throwing() throws Exception {
        CAPTURING_URL_HANDLER.setResponse(500, "{\"success\":false,\"message\":\"boom\"}");

        Map<String, Object> ipInfo = IpApiClient.getIpInfo("8.8.8.8").get(2, TimeUnit.SECONDS);

        assertNull(ipInfo);
        assertEquals(1, CAPTURING_URL_HANDLER.openCount());
    }

    @Test
    void io_exception_from_provider_is_swallowed_and_returns_null() throws Exception {
        CAPTURING_URL_HANDLER.setThrowOnConnect(new SocketTimeoutException("connect timed out"));

        Map<String, Object> ipInfo = IpApiClient.getIpInfo("8.8.8.8").get(2, TimeUnit.SECONDS);

        assertNull(ipInfo);
        assertEquals(1, CAPTURING_URL_HANDLER.openCount());
    }

    private static final class CapturingUrlHandlerFactory implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if ("http".equals(protocol) || "https".equals(protocol)) return CAPTURING_URL_HANDLER;
            return null;
        }
    }

    private static final class CapturingUrlHandler extends URLStreamHandler {
        private final AtomicInteger openCount = new AtomicInteger();
        private final AtomicReference<URL> lastUrl = new AtomicReference<>();
        private volatile int responseCode = HttpURLConnection.HTTP_OK;
        private volatile String responseBody = SUCCESS_BODY;
        private volatile IOException throwOnConnect;

        @Override
        protected URLConnection openConnection(URL url) {
            openCount.incrementAndGet();
            lastUrl.set(url);
            return new CapturingHttpConnection(url, responseCode, responseBody, throwOnConnect);
        }

        void reset() {
            openCount.set(0);
            lastUrl.set(null);
            responseCode = HttpURLConnection.HTTP_OK;
            responseBody = SUCCESS_BODY;
            throwOnConnect = null;
        }

        void setResponse(int code, String body) {
            this.responseCode = code;
            this.responseBody = body;
            this.throwOnConnect = null;
        }

        void setThrowOnConnect(IOException exception) {
            this.throwOnConnect = exception;
        }

        int openCount() {
            return openCount.get();
        }
    }

    private static final class CapturingHttpConnection extends HttpURLConnection {
        private final int responseCode;
        private final String body;
        private final IOException throwOnConnect;

        CapturingHttpConnection(URL url, int responseCode, String body, IOException throwOnConnect) {
            super(url);
            this.responseCode = responseCode;
            this.body = body;
            this.throwOnConnect = throwOnConnect;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() throws IOException {
            if (throwOnConnect != null) throw throwOnConnect;
        }

        @Override
        public int getResponseCode() throws IOException {
            if (throwOnConnect != null) throw throwOnConnect;
            return responseCode;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (throwOnConnect != null) throw throwOnConnect;
            if (responseCode < 200 || responseCode >= 300) throw new IOException("HTTP " + responseCode);
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            if (responseCode < 200 || responseCode >= 300) {
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }
    }
}
