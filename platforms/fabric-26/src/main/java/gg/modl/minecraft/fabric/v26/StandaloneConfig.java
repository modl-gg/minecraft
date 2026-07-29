package gg.modl.minecraft.fabric.v26;

import gg.modl.minecraft.core.boot.SyncPollingRate;
import lombok.Getter;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Getter
public class StandaloneConfig {
    private final boolean debugMode;
    private final boolean queryMojang;
    private final int syncPollingRate;
    private final List<String> mutedCommands;

    private StandaloneConfig(boolean debugMode, boolean queryMojang, int syncPollingRate, List<String> mutedCommands) {
        this.debugMode = debugMode;
        this.queryMojang = queryMojang;
        this.syncPollingRate = syncPollingRate;
        this.mutedCommands = mutedCommands;
    }

    public static StandaloneConfig read(Path configPath, Logger logger) {
        boolean debugMode = false;
        boolean queryMojang = false;
        int syncPollingRate = SyncPollingRate.DEFAULT_SECONDS;
        List<String> mutedCommands = List.of();

        if (configPath.toFile().exists()) {
            try (InputStream in = Files.newInputStream(configPath)) {
                Map<String, Object> config = new Yaml().load(in);
                if (config != null) {
                    debugMode = Boolean.TRUE.equals(config.get("debug"));
                    Object serverObj = config.get("server");
                    if (serverObj instanceof Map<?, ?> serverMap) {
                        queryMojang = Boolean.TRUE.equals(serverMap.get("query_mojang"));
                    }
                    Object syncObj = config.get("sync");
                    if (syncObj instanceof Map<?, ?> syncMap) {
                        Object rate = syncMap.get("polling_rate");
                        if (rate instanceof Number n) syncPollingRate = SyncPollingRate.clamp(n.intValue());
                    }
                    Object mutedObj = config.get("muted_commands");
                    if (mutedObj instanceof List<?> list) {
                        mutedCommands = list.stream().map(Object::toString).toList();
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to read config.yml: {}", e.getMessage());
            }
        }
        return new StandaloneConfig(debugMode, queryMojang, syncPollingRate, mutedCommands);
    }
}
