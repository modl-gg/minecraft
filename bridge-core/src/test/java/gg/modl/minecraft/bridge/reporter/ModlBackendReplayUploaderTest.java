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
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
