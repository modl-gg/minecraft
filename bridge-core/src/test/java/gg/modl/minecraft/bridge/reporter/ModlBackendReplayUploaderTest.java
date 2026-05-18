package gg.modl.minecraft.bridge.reporter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.UUID;
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
        List<String> initBodies = new CopyOnWriteArrayList<>();
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        server.createContext("/v1/minecraft/replays/upload", exchange -> {
            observedPaths.add(exchange.getRequestURI().getPath());
            initBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
            UUID targetUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
            ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                    baseUrl + "/v2", "key", "panel.example.com", Logger.getLogger("test"));

            String replayId = uploader.uploadAsync(replayFile, "1.21.11", targetUuid, "modltarget").get();

            assertEquals("replay-123", replayId);
            assertEquals(List.of(
                    "/v1/minecraft/replays/upload",
                    "/storage",
                    "/v1/minecraft/replays/confirm/replay-123"
            ), observedPaths);
            assertEquals(1, initBodies.size());
            JsonObject initBody = JsonParser.parseString(initBodies.get(0)).getAsJsonObject();
            assertEquals("1.21.11", initBody.get("mcVersion").getAsString());
            assertEquals(replayFile.length(), initBody.get("fileSize").getAsLong());
            assertEquals(targetUuid.toString(), initBody.get("targetUuid").getAsString());
            assertEquals("modltarget", initBody.get("targetName").getAsString());
        } finally {
            server.stop(0);
        }
    }
}
