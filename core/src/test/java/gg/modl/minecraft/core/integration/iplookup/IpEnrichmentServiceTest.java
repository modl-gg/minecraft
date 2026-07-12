package gg.modl.minecraft.core.integration.iplookup;

import gg.modl.minecraft.core.support.StubConnectionOpener;
import gg.modl.minecraft.core.util.BoundedLookupExecutor;
import gg.modl.minecraft.core.util.HttpConnectionOpener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpEnrichmentServiceTest {
    private static final String SUCCESS_BODY = "{\"ip\":\"8.8.8.8\",\"success\":true,\"country_code\":\"US\",\"region\":\"California\",\"city\":\"Mountain View\",\"connection\":{\"isp\":\"Google LLC\"},\"security\":{\"proxy\":false,\"hosting\":true}}";

    private static IpEnrichmentService service(HttpConnectionOpener opener) {
        return new IpEnrichmentService(IpEnrichmentService.DEFAULT_ENABLED, IpEnrichmentService.DEFAULT_URL_TEMPLATE, opener);
    }

    @Test
    void lookupExecutorThreadsAreBoundedDaemonAndNamed() throws Exception {
        IpEnrichmentService service = service(new StubConnectionOpener());
        Field field = IpEnrichmentService.class.getDeclaredField("lookupExecutor");
        field.setAccessible(true);
        BoundedLookupExecutor lookupExecutor = (BoundedLookupExecutor) field.get(service);
        Field poolField = BoundedLookupExecutor.class.getDeclaredField("pool");
        poolField.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) poolField.get(lookupExecutor);

        Thread thread = executor.getThreadFactory().newThread(() -> {});

        assertTrue(thread.isDaemon());
        assertTrue(thread.getName().startsWith("modl-ip-api-"));
        assertTrue(executor.getMaximumPoolSize() <= 2);
        assertFalse(executor.getQueue().remainingCapacity() == Integer.MAX_VALUE);
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, executor.getRejectedExecutionHandler());
    }

    @Test
    void publicIpIsLookedUpViaHttpsProviderByDefault() throws Exception {
        StubConnectionOpener opener = new StubConnectionOpener().respondWith(HttpURLConnection.HTTP_OK, SUCCESS_BODY);

        Map<String, Object> ipInfo = service(opener).getIpInfo("8.8.8.8").get(2, TimeUnit.SECONDS);

        assertNotNull(ipInfo);
        assertEquals(1, opener.openCount());
        assertEquals("success", ipInfo.get("status"));
        assertEquals("US", ipInfo.get("countryCode"));
        assertEquals("California", ipInfo.get("regionName"));
        assertEquals("Google LLC", ipInfo.get("as"));
        assertEquals(Boolean.FALSE, ipInfo.get("proxy"));
        assertEquals(Boolean.TRUE, ipInfo.get("hosting"));
    }

    @Test
    void legacyIsPrefixedSecurityKeysAreHonored() throws Exception {
        StubConnectionOpener opener = new StubConnectionOpener().respondWith(HttpURLConnection.HTTP_OK,
                "{\"ip\":\"8.8.8.8\",\"success\":true,\"country_code\":\"US\",\"connection\":{\"isp\":\"Google LLC\"},\"security\":{\"is_proxy\":true,\"is_hosting\":true}}");

        Map<String, Object> ipInfo = service(opener).getIpInfo("8.8.8.8").get(2, TimeUnit.SECONDS);

        assertNotNull(ipInfo);
        assertEquals(Boolean.TRUE, ipInfo.get("proxy"));
        assertEquals(Boolean.TRUE, ipInfo.get("hosting"));
    }

    @Test
    void malformedIpAddressesReturnLocalInfoWithoutExternalLookup() throws Exception {
        StubConnectionOpener opener = new StubConnectionOpener();

        Map<String, Object> ipInfo = service(opener).getIpInfo("999.999.999.999").get(1, TimeUnit.SECONDS);

        assertEquals("success", ipInfo.get("status"));
        assertEquals("XX", ipInfo.get("countryCode"));
        assertEquals(0, opener.openCount());
    }

    @Test
    void privateAndLinkLocalAddressesReturnLocalInfoWithoutExternalLookup() throws Exception {
        StubConnectionOpener opener = new StubConnectionOpener();

        Map<String, Object> ipInfo = service(opener).getIpInfo("169.254.10.20").get(1, TimeUnit.SECONDS);

        assertEquals("success", ipInfo.get("status"));
        assertEquals("XX", ipInfo.get("countryCode"));
        assertEquals(0, opener.openCount());
    }

    @Test
    void disabledLookupReturnsNullForPublicIpWithoutOpeningConnection() throws Exception {
        StubConnectionOpener opener = new StubConnectionOpener();
        IpEnrichmentService service = new IpEnrichmentService(false, IpEnrichmentService.DEFAULT_URL_TEMPLATE, opener);

        Map<String, Object> ipInfo = service.getIpInfo("8.8.8.8").get(1, TimeUnit.SECONDS);

        assertNull(ipInfo);
        assertEquals(0, opener.openCount());
    }

    @Test
    void plaintextHttpSchemeIsRefusedEvenIfConfigured() throws Exception {
        StubConnectionOpener opener = new StubConnectionOpener();
        IpEnrichmentService service = new IpEnrichmentService(true, "http://example.com/{ip}", opener);

        Map<String, Object> ipInfo = service.getIpInfo("8.8.8.8").get(1, TimeUnit.SECONDS);

        assertNull(ipInfo);
        assertEquals(0, opener.openCount());
    }

    @Test
    void serverErrorResponsesReturnNullWithoutThrowing() throws Exception {
        StubConnectionOpener opener = new StubConnectionOpener().respondWith(500, "{\"success\":false,\"message\":\"boom\"}");

        Map<String, Object> ipInfo = service(opener).getIpInfo("8.8.8.8").get(2, TimeUnit.SECONDS);

        assertNull(ipInfo);
        assertEquals(1, opener.openCount());
    }

    @Test
    void ioExceptionFromProviderIsSwallowedAndReturnsNull() throws Exception {
        StubConnectionOpener opener = new StubConnectionOpener().throwOnConnect(new SocketTimeoutException("connect timed out"));

        Map<String, Object> ipInfo = service(opener).getIpInfo("8.8.8.8").get(2, TimeUnit.SECONDS);

        assertNull(ipInfo);
        assertEquals(1, opener.openCount());
    }
}
