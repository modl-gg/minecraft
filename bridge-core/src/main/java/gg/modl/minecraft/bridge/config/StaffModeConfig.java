package gg.modl.minecraft.bridge.config;

import lombok.Data;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class StaffModeConfig {

    private static final String ACTION_TARGET_SELECTOR = "target_selector";
    private static final String ACTION_VANISH_TOGGLE = "vanish_toggle";
    private static final String ACTION_STAFF_MENU = "staff_menu";

    @Getter private boolean vanishOnEnable = true;
    @Getter private Map<Integer, HotbarItem> staffHotbar = new LinkedHashMap<>();
    @Getter private Map<Integer, HotbarItem> targetHotbar = new LinkedHashMap<>();
    @Getter private ScoreboardConfig staffScoreboard = new ScoreboardConfig();
    @Getter private ScoreboardConfig targetScoreboard = new ScoreboardConfig();

    @SuppressWarnings("unchecked")
    public StaffModeConfig(Path dataFolder, Logger logger) {
        File configFile = dataFolder.resolve("staff_mode.yml").toFile();
        if (!configFile.exists()) {
            setDefaults();
            return;
        }

        try (InputStream is = new FileInputStream(configFile)) {
            Map<String, Object> data = new Yaml().load(is);
            if (data == null) {
                setDefaults();
                return;
            }

            vanishOnEnable = !Boolean.FALSE.equals(data.getOrDefault("vanish_on_enable", true));

            Optional.ofNullable((Map<?, ?>) data.get("staff_hotbar")).map(this::parseHotbar).ifPresent(h -> staffHotbar = h);
            Optional.ofNullable((Map<?, ?>) data.get("target_hotbar")).map(this::parseHotbar).ifPresent(h -> targetHotbar = h);
            Optional.ofNullable((Map<?, ?>) data.get("staff_scoreboard")).map(this::parseScoreboard).ifPresent(s -> staffScoreboard = s);
            Optional.ofNullable((Map<?, ?>) data.get("target_scoreboard")).map(this::parseScoreboard).ifPresent(s -> targetScoreboard = s);

            if (staffHotbar.isEmpty() && targetHotbar.isEmpty()) {
                setDefaults();
            }
        } catch (Exception e) {
            logger.warning("[StaffMode] Failed to load staff_mode.yml: " + e.getMessage());
            setDefaults();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, HotbarItem> parseHotbar(Map<?, ?> raw) {
        Map<Integer, HotbarItem> result = new LinkedHashMap<>();
        if (raw == null) return result;

        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            try {
                int slot = entry.getKey() instanceof Number
                        ? ((Number) entry.getKey()).intValue()
                        : Integer.parseInt(entry.getKey().toString());
                Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
                HotbarItem item = new HotbarItem();
                item.item = (String) itemData.getOrDefault("item", "minecraft:stone");
                item.name = (String) itemData.getOrDefault("name", "");
                item.action = (String) itemData.getOrDefault("action", "");
                item.toggleItem = (String) itemData.get("toggle_item");
                item.toggleName = (String) itemData.get("toggle_name");
                item.lore = parseStringList(itemData.get("lore"));
                item.toggleLore = parseStringList(itemData.get("toggle_lore"));
                result.put(slot, item);
            } catch (Exception ignored) {}
        }
        return result;
    }

    private List<String> parseStringList(Object value) {
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            return list.stream()
                    .map(o -> o != null ? String.valueOf(o) : "")
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private ScoreboardConfig parseScoreboard(Map<?, ?> raw) {
        ScoreboardConfig config = new ScoreboardConfig();
        if (raw == null) return config;
        if (raw.containsKey("enabled")) config.enabled = Boolean.TRUE.equals(raw.get("enabled"));
        if (raw.containsKey("title")) config.title = String.valueOf(raw.get("title"));
        if (raw.containsKey("vanish")) config.vanish = String.valueOf(raw.get("vanish"));
        config.lines = parseStringList(raw.get("lines"));
        return config;
    }

    private void setDefaults() {
        staffHotbar.put(0, createHotbarItem("minecraft:lead", "&eTarget Player", ACTION_TARGET_SELECTOR));
        staffHotbar.put(3, createVanishToggleItem());
        staffHotbar.put(8, createHotbarItem("minecraft:compass", "&6Staff Menu", ACTION_STAFF_MENU));

        targetHotbar.put(0, createHotbarItem("minecraft:ice", "&bFreeze Target", "freeze_target"));
        targetHotbar.put(3, createHotbarItem("minecraft:nether_star", "&cStop Targeting", "stop_target"));
        targetHotbar.put(4, createHotbarItem("minecraft:book", "&eInspect Target", "inspect_target"));
        targetHotbar.put(5, createHotbarItem("minecraft:chest", "&eOpen Inventory", "open_inventory"));
        targetHotbar.put(7, createVanishToggleItem());
    }

    private static HotbarItem createHotbarItem(String material, String name, String action) {
        HotbarItem item = new HotbarItem();
        item.item = material;
        item.name = name;
        item.action = action;
        return item;
    }

    private static HotbarItem createVanishToggleItem() {
        HotbarItem item = createHotbarItem("minecraft:lime_dye", "&aVanish: ON", ACTION_VANISH_TOGGLE);
        item.toggleItem = "minecraft:gray_dye";
        item.toggleName = "&7Vanish: OFF";
        return item;
    }

    @Data
    public static class HotbarItem {
        private String item = "minecraft:stone";
        private String name = "";
        private String action = "";
        private String toggleItem;
        private String toggleName;
        private List<String> lore = new ArrayList<>();
        private List<String> toggleLore = new ArrayList<>();
    }

    @Data
    public static class ScoreboardConfig {
        private boolean enabled = false;
        private String title = "";
        private String vanish = "";
        private List<String> lines = new ArrayList<>();
    }
}
