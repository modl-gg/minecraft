package gg.modl.minecraft.core.impl.http;

import gg.modl.minecraft.api.http.ApiClientException;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.util.CircuitBreaker;
import gg.modl.minecraft.core.util.Java8Collections;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class AbstractModlHttpTransport {
    protected static final String HEADER_API_KEY = "X-API-Key", HEADER_SERVER_DOMAIN = "X-Server-Domain",
        HEADER_CONTENT_TYPE = "Content-Type", HEADER_ACTING_STAFF_ID = "X-Acting-Staff-Id", HEADER_USER_AGENT = "User-Agent";
    protected static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10), LOGIN_TIMEOUT = Duration.ofSeconds(15),
        SYNC_TIMEOUT = Duration.ofSeconds(20);
    protected static final int STATUS_UNREACHABLE = -1;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5L;
    private static final byte[] EMPTY_BODY = new byte[0];

    protected final @NotNull String baseUrl, apiKey, serverDomain;
    protected final @NotNull ThreadPoolExecutor executor;
    protected final @NotNull Logger logger;
    protected final @NotNull CircuitBreaker backgroundCircuitBreaker;
    protected final @NotNull CircuitBreaker loginCircuitBreaker;
    protected final boolean debugMode;
    private final @NotNull String versionTag;

    protected AbstractModlHttpTransport(@NotNull String baseUrl, @NotNull String apiKey, @NotNull String serverDomain,
                                        boolean debugMode, @NotNull String versionTag, @NotNull String threadNamePrefix) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.serverDomain = serverDomain;
        this.debugMode = debugMode;
        this.versionTag = versionTag;
        this.backgroundCircuitBreaker = new CircuitBreaker();
        this.loginCircuitBreaker = new CircuitBreaker();

        AtomicInteger threadCounter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(0, 8, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, threadNamePrefix + threadCounter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
        this.logger = Logger.getLogger(getClass().getName());
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    protected final String generateRequestId() {
        return versionTag + "-" + (System.nanoTime() % 1000000);
    }

    protected final <R> CompletableFuture<R> execute(HttpRequest request, String operation, CircuitBreaker breaker,
                                                     ResponseDecoder<R> decoder) {
        final Instant startTime = Instant.now();
        final String requestId = generateRequestId();

        if (!breaker.allowRequest()) {
            return Java8Collections.failedFuture(new PanelUnavailableException(request.url,
                HttpURLConnection.HTTP_UNAVAILABLE, versionTag + " API is temporarily unavailable (circuit breaker open)"));
        }

        if (debugMode) logRequest(requestId, request);

        final CompletableFuture<R> pending;
        try {
            pending = submit(request, operation, breaker, decoder, startTime, requestId);
        } catch (RejectedExecutionException rejected) {
            breaker.releaseProbe();
            return Java8Collections.failedFuture(new PanelUnavailableException(request.url,
                HttpURLConnection.HTTP_UNAVAILABLE, versionTag + " API request rejected (local executor saturated)"));
        }

        return pending.exceptionally(throwable -> {
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
            if (!(cause instanceof ApiClientException)) breaker.recordFailure();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(versionTag + " HTTP request failed", throwable);
        });
    }

    private <R> CompletableFuture<R> submit(HttpRequest request, String operation, CircuitBreaker breaker,
                                            ResponseDecoder<R> decoder, Instant startTime, String requestId) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(request);
                int statusCode = connection.getResponseCode();
                byte[] responseBody = readBody(requestId, connection, statusCode);
                long durationMs = Duration.between(startTime, Instant.now()).toMillis();

                if (debugMode) logResponse(requestId, statusCode, responseBody, durationMs, operation);

                if (statusCode >= 200 && statusCode < 300) {
                    breaker.recordSuccess();
                    return decoder.decode(requestId, responseBody);
                }
                throw toError(requestId, request, statusCode, responseBody);
            } catch (RuntimeException e) {
                throw e;
            } catch (IOException e) {
                throw new PanelUnavailableException(request.url, STATUS_UNREACHABLE,
                    versionTag + " API unreachable: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            } catch (Exception e) {
                throw new RuntimeException(versionTag + " HTTP request failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, executor);
    }

    private HttpURLConnection open(HttpRequest request) throws IOException {
        URL url = new URL(request.url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(request.method);
        connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        connection.setReadTimeout((int) (request.timeout != null ? request.timeout : CONNECT_TIMEOUT).toMillis());
        connection.setInstanceFollowRedirects(true);

        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }

        if (request.body != null) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(request.body);
            }
        }
        return connection;
    }

    private byte[] readBody(String requestId, HttpURLConnection connection, int statusCode) {
        try {
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) return EMPTY_BODY;
            try (InputStream in = stream) {
                return readAllBytes(in);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, String.format("[%s-REQ-%s] Failed to read response body", versionTag, requestId), e);
            return EMPTY_BODY;
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    protected abstract void logRequest(String requestId, HttpRequest request);

    protected abstract void logResponse(String requestId, int statusCode, byte[] body, long durationMs, String operation);

    protected abstract RuntimeException toError(String requestId, HttpRequest request, int statusCode, byte[] body);

    @FunctionalInterface
    protected interface ResponseDecoder<R> {
        R decode(String requestId, byte[] body) throws Exception;
    }

    protected static final class HttpRequest {
        final String url;
        final String method;
        final byte[] body;
        final Duration timeout;
        final Map<String, String> headers;

        HttpRequest(String url, String method, byte[] body, Duration timeout, Map<String, String> headers) {
            this.url = url;
            this.method = method;
            this.body = body;
            this.timeout = timeout;
            this.headers = headers;
        }
    }
}
