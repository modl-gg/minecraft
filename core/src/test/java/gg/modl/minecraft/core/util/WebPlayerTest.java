package gg.modl.minecraft.core.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPlayerTest {

    // Cache-hit test is intentionally omitted: production WebPlayer has no caching layer.

    private static final MojangCapturingUrlHandler CAPTURING_URL_HANDLER = new MojangCapturingUrlHandler();
    private static URLStreamHandlerFactory previousFactory;

    @BeforeAll
    static void installCapturingFactory() throws Exception {
        // Only one URLStreamHandlerFactory may be installed per JVM, and URL caches
        // per-protocol handlers in URL.handlers. Sibling tests (e.g. IpApiClientTest)
        // install their own at class load and prime the cache. To guarantee our handler
        // is what receives Mojang requests during this test class, we swap the static
        // factory field via Unsafe, clear the protocol cache, and restore afterward.
        previousFactory = currentFactory();
        replaceFactory(null);
        clearHandlerCache();
        URL.setURLStreamHandlerFactory(new MojangCapturingUrlHandlerFactory());
    }

    @AfterAll
    static void restoreFactory() throws Exception {
        WebPlayer.shutdown();
        replaceFactory(previousFactory);
        clearHandlerCache();
    }

    @BeforeEach
    void resetHandler() {
        CAPTURING_URL_HANDLER.reset();
    }

    @Test
    void mojang_lookup_executor_threads_are_bounded_daemon_and_named() throws Exception {
        Field field = WebPlayer.class.getDeclaredField("LOOKUP_EXECUTOR");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(null);

        Thread thread = executor.getThreadFactory().newThread(() -> {});

        assertTrue(thread.isDaemon());
        assertTrue(thread.getName().startsWith("modl-web-player-"));
        assertTrue(executor.getMaximumPoolSize() <= 4);
        assertFalse(executor.getQueue().remainingCapacity() == Integer.MAX_VALUE);
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, executor.getRejectedExecutionHandler());
    }

    @Test
    void lookupReturnsResultWithoutBlockingCaller() throws Exception {
        CAPTURING_URL_HANDLER.respondWith(HttpURLConnection.HTTP_OK,
                "{\"name\":\"byteful\",\"id\":\"00112233445566778899aabbccddeeff\"}");

        Thread callerThread = Thread.currentThread();
        CompletableFuture<WebPlayer> lookup = WebPlayer.get("byteful");
        WebPlayer result = lookup.get(2, TimeUnit.SECONDS);

        assertEquals(1, CAPTURING_URL_HANDLER.openCount(),
                "capturing URL handler was not invoked (likely stomped by a sibling test)");
        assertTrue(result.isValid());
        assertEquals("byteful", result.getName());
        assertEquals(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"), result.getUuid());
        assertNotNull(CAPTURING_URL_HANDLER.lastConnectionThread());
        assertNotEquals(callerThread, CAPTURING_URL_HANDLER.lastConnectionThread(),
                "lookup must run off the calling thread");
    }

    @Test
    void lookupFailureReturnsInvalidGracefully() throws Exception {
        CAPTURING_URL_HANDLER.respondWith(HttpURLConnection.HTTP_INTERNAL_ERROR, "");

        WebPlayer result = WebPlayer.get("byteful").get(2, TimeUnit.SECONDS);

        assertEquals(1, CAPTURING_URL_HANDLER.openCount(),
                "capturing URL handler was not invoked (likely stomped by a sibling test)");
        assertFalse(result.isValid());
    }

    private static URLStreamHandlerFactory currentFactory() throws Exception {
        Unsafe unsafe = unsafe();
        Field field = URL.class.getDeclaredField("factory");
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        return (URLStreamHandlerFactory) unsafe.getObject(base, offset);
    }

    private static void replaceFactory(URLStreamHandlerFactory factory) throws Exception {
        Unsafe unsafe = unsafe();
        Field field = URL.class.getDeclaredField("factory");
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        unsafe.putObjectVolatile(base, offset, factory);
    }

    @SuppressWarnings("unchecked")
    private static void clearHandlerCache() throws Exception {
        Unsafe unsafe = unsafe();
        Field field = URL.class.getDeclaredField("handlers");
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Hashtable<String, URLStreamHandler> handlers =
                (Hashtable<String, URLStreamHandler>) unsafe.getObject(base, offset);
        if (handlers != null) handlers.clear();
    }

    private static Unsafe unsafe() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    private static final class MojangCapturingUrlHandlerFactory implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if ("http".equals(protocol) || "https".equals(protocol)) return CAPTURING_URL_HANDLER;
            return null;
        }
    }

    private static final class MojangCapturingUrlHandler extends URLStreamHandler {
        private final AtomicInteger openCount = new AtomicInteger();
        private final AtomicReference<Thread> lastThread = new AtomicReference<>();
        private volatile int responseCode = HttpURLConnection.HTTP_OK;
        private volatile String responseBody = "{}";

        @Override
        protected URLConnection openConnection(URL url) {
            openCount.incrementAndGet();
            lastThread.set(Thread.currentThread());
            return new StubHttpConnection(url, responseCode, responseBody);
        }

        void respondWith(int code, String body) {
            this.responseCode = code;
            this.responseBody = body;
        }

        void reset() {
            openCount.set(0);
            lastThread.set(null);
            responseCode = HttpURLConnection.HTTP_OK;
            responseBody = "{}";
        }

        int openCount() {
            return openCount.get();
        }

        Thread lastConnectionThread() {
            return lastThread.get();
        }
    }

    private static final class StubHttpConnection extends HttpURLConnection {
        private final int code;
        private final String body;

        StubHttpConnection(URL url, int code, String body) {
            super(url);
            this.code = code;
            this.body = body;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public int getResponseCode() {
            return code;
        }

        @Override
        public ByteArrayInputStream getInputStream() throws IOException {
            if (code != HTTP_OK) throw new IOException("HTTP " + code);
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
