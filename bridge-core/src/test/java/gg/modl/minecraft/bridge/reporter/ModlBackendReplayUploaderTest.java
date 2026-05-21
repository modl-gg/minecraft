package gg.modl.minecraft.bridge.reporter;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModlBackendReplayUploaderTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsVersionedBackendBaseUrls() throws IOException, ExecutionException, InterruptedException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> observedPaths = new CopyOnWriteArrayList<>();
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = ("{\"replayId\":\"replay-123\",\"uploadUrl\":\"" + baseUrl + "/storage\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/storage", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/v1/minecraft/replays/confirm/replay-123", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(404, 0);
            exchange.close();
        });
        server.start();

        try {
            File replayFile = Files.write(tempDir.resolve("sample.replay"), "replay".getBytes(StandardCharsets.UTF_8)).toFile();
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl + "/v2", "key", "panel.example.com", Logger.getLogger("test"));

            String replayId = uploader.uploadAsync(replayFile, "1.21.11").get();

            assertEquals("replay-123", replayId);
            assertEquals(List.of(
                    "/v1/minecraft/replays/upload",
                    "/storage",
                    "/v1/minecraft/replays/confirm/replay-123"
            ), observedPaths);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsApiKeyOnlyToTrustedBackendRequests() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> observedPaths = new CopyOnWriteArrayList<>();
        List<String> backendApiKeys = new CopyOnWriteArrayList<>();
        List<String> backendServerDomains = new CopyOnWriteArrayList<>();
        List<String> storageApiKeys = new CopyOnWriteArrayList<>();
        List<String> storageContentLengths = new CopyOnWriteArrayList<>();
        List<String> storageBodies = new CopyOnWriteArrayList<>();
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            backendApiKeys.add(exchange.getRequestHeaders().getFirst("X-API-Key"));
            backendServerDomains.add(exchange.getRequestHeaders().getFirst("X-Server-Domain"));
            byte[] response = ("{\"replayId\":\"replay-headers\",\"uploadUrl\":\"" + baseUrl + "/storage?signature=abc\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/storage", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            storageApiKeys.add(exchange.getRequestHeaders().getFirst("X-API-Key"));
            storageContentLengths.add(exchange.getRequestHeaders().getFirst("Content-Length"));
            storageBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/v1/minecraft/replays/confirm/replay-headers", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            backendApiKeys.add(exchange.getRequestHeaders().getFirst("X-API-Key"));
            backendServerDomains.add(exchange.getRequestHeaders().getFirst("X-Server-Domain"));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        try {
            File replayFile = Files.write(tempDir.resolve("headers.replay"), "replay".getBytes(StandardCharsets.UTF_8)).toFile();
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl, "secret-key", "server.example.com", Logger.getLogger("test"));

            String replayId = uploader.uploadAsync(replayFile, "1.21.11").get();

            assertEquals("replay-headers", replayId);
            assertEquals(List.of(
                    "/v1/minecraft/replays/upload",
                    "/storage",
                    "/v1/minecraft/replays/confirm/replay-headers"
            ), observedPaths);
            assertEquals(List.of("secret-key", "secret-key"), backendApiKeys);
            assertEquals(List.of("server.example.com", "server.example.com"), backendServerDomains);
            assertEquals(1, storageApiKeys.size());
            assertNull(storageApiKeys.get(0));
            assertEquals(List.of(String.valueOf(replayFile.length())), storageContentLengths);
            assertEquals(List.of("replay"), storageBodies);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void propagatesInitFailureAndSkipsStorageAndConfirm() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> observedPaths = new CopyOnWriteArrayList<>();
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = "not allowed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        server.start();

        try {
            File replayFile = Files.write(tempDir.resolve("init-failure.replay"), "replay".getBytes(StandardCharsets.UTF_8)).toFile();
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl, "secret-key", "server.example.com", Logger.getLogger("test"));

            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> uploader.uploadAsync(replayFile, "1.21.11").get());

            assertTrue(exception.getCause().getMessage().contains("Replay upload failed"));
            assertTrue(exception.getCause().getCause().getMessage().contains("Init upload failed (HTTP 403): not allowed"));
            assertEquals(List.of("/v1/minecraft/replays/upload"), observedPaths);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void propagatesStorageFailureAndSkipsConfirm() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> observedPaths = new CopyOnWriteArrayList<>();
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = ("{\"replayId\":\"replay-storage-failure\",\"uploadUrl\":\"" + baseUrl + "/storage\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/storage", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] response = "storage rejected".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        server.start();

        try {
            File replayFile = Files.write(tempDir.resolve("storage-failure.replay"), "replay".getBytes(StandardCharsets.UTF_8)).toFile();
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl, "secret-key", "server.example.com", Logger.getLogger("test"));

            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> uploader.uploadAsync(replayFile, "1.21.11").get());

            assertTrue(exception.getCause().getCause().getMessage().contains("Storage upload failed (HTTP 503): storage rejected"));
            assertEquals(List.of("/v1/minecraft/replays/upload", "/storage"), observedPaths);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void propagatesConfirmFailureAfterStorageUpload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> observedPaths = new CopyOnWriteArrayList<>();
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = ("{\"replayId\":\"replay-confirm-failure\",\"uploadUrl\":\"" + baseUrl + "/storage\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/storage", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/v1/minecraft/replays/confirm/replay-confirm-failure", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = "confirm rejected".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            File replayFile = Files.write(tempDir.resolve("confirm-failure.replay"), "replay".getBytes(StandardCharsets.UTF_8)).toFile();
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl, "secret-key", "server.example.com", Logger.getLogger("test"));

            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> uploader.uploadAsync(replayFile, "1.21.11").get());

            assertTrue(exception.getCause().getCause().getMessage().contains(
                    "Confirm upload failed (HTTP 502) for replay replay-confirm-failure: confirm rejected"));
            assertEquals(List.of(
                    "/v1/minecraft/replays/upload",
                    "/storage",
                    "/v1/minecraft/replays/confirm/replay-confirm-failure"
            ), observedPaths);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnsupportedOrMalformedBackendUrlsBeforeUpload() {
        for (String backendUrl : List.of("ftp://example.com", "file:///tmp/backend", "http:///missing-host", "://bad")) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new ModlBackendReplayUploader(backendUrl, "secret-key", "server.example.com", Logger.getLogger("test")));

            assertTrue(exception.getMessage().contains("Unsupported backend URL"));
        }
    }

    @Test
    void acceptsLocalHttpBackendUrlsForDevelopmentAndTests() {
        assertDoesNotThrow(() -> new ModlBackendReplayUploader(
                "http://127.0.0.1:8080/v2", "secret-key", "server.example.com", Logger.getLogger("test")));
    }

    @Test
    void rejectsRemoteHttpBackendUrlsBeforeUpload() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ModlBackendReplayUploader(
                        "http://api.example.com/v2", "secret-key", "server.example.com", Logger.getLogger("test")));

        assertTrue(exception.getMessage().contains("Unsupported backend URL"));
        assertTrue(exception.getMessage().contains("http requires a loopback host"));
    }

    @Test
    void rejectsRemoteHttpPresignedUploadUrlsBeforeStorageRequest() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> observedPaths = new CopyOnWriteArrayList<>();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            byte[] response = ("{\"replayId\":\"replay-remote-http-storage\","
                    + "\"uploadUrl\":\"http://storage.example.com/upload?signature=abc\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        server.start();

        try {
            File replayFile = Files.write(tempDir.resolve("remote-http-storage.replay"),
                    "replay".getBytes(StandardCharsets.UTF_8)).toFile();
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl, "secret-key", "server.example.com", Logger.getLogger("test"));

            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> uploader.uploadAsync(replayFile, "1.21.11").get());

            assertTrue(exception.getCause().getCause().getMessage().contains("Unsupported presigned upload URL"));
            assertTrue(exception.getCause().getCause().getMessage().contains("http requires a loopback host"));
            assertEquals(List.of("/v1/minecraft/replays/upload"), observedPaths);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runsUploadWorkOnProvidedExecutor() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean executorUsed = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(() -> {
                executorUsed.set(true);
                r.run();
            }, "modl-replay-upload-test");
            thread.setDaemon(true);
            return thread;
        });
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            byte[] response = ("{\"replayId\":\"replay-executor\",\"uploadUrl\":\"" + baseUrl + "/storage\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/storage", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/v1/minecraft/replays/confirm/replay-executor", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        try {
            File replayFile = Files.write(tempDir.resolve("executor.replay"), "replay".getBytes(StandardCharsets.UTF_8)).toFile();
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl, "secret-key", "server.example.com", Logger.getLogger("test"), executor);

            assertEquals("replay-executor", uploader.uploadAsync(replayFile, "1.21.11").get());
            assertTrue(executorUsed.get());
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void closeShutsDownDefaultExecutor() throws Exception {
        ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                "http://127.0.0.1:8080", "secret-key", "server.example.com", Logger.getLogger("test"));

        uploader.close();

        File replayFile = Files.write(tempDir.resolve("closed.replay"), "replay".getBytes(StandardCharsets.UTF_8)).toFile();
        assertThrows(RejectedExecutionException.class, () -> uploader.uploadAsync(replayFile, "1.21.11"));
    }

    @Test
    void closeCompletesQueuedDefaultExecutorUploads() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService serverExecutor = Executors.newCachedThreadPool();
        CountDownLatch firstUploadStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstUpload = new CountDownLatch(1);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        server.setExecutor(serverExecutor);
        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            firstUploadStarted.countDown();
            try {
                releaseFirstUpload.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] response = ("{\"replayId\":\"replay-close\",\"uploadUrl\":\"" + baseUrl + "/storage\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/storage", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/v1/minecraft/replays/confirm/replay-close", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        try {
            File firstReplayFile = Files.write(tempDir.resolve("first-close.replay"),
                    "first".getBytes(StandardCharsets.UTF_8)).toFile();
            File secondReplayFile = Files.write(tempDir.resolve("second-close.replay"),
                    "second".getBytes(StandardCharsets.UTF_8)).toFile();
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl, "secret-key", "server.example.com", Logger.getLogger("test"));

            CompletableFuture<String> firstUpload = uploader.uploadAsync(firstReplayFile, "1.21.11");
            assertTrue(firstUploadStarted.await(5, TimeUnit.SECONDS));
            CompletableFuture<String> queuedUpload = uploader.uploadAsync(secondReplayFile, "1.21.11");

            uploader.close();

            assertTrue(queuedUpload.isDone());
            assertTrue(queuedUpload.isCompletedExceptionally());
            assertTrue(firstUpload.isDone());
            assertTrue(firstUpload.isCompletedExceptionally());
        } finally {
            releaseFirstUpload.countDown();
            server.stop(0);
            serverExecutor.shutdownNow();
            serverExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void closeKeepsProvidedExecutorCallerOwned() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    "http://127.0.0.1:8080", "secret-key", "server.example.com", Logger.getLogger("test"), executor);

            uploader.close();

            assertFalse(executor.isShutdown());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
