package gg.modl.minecraft.core.locale;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LocaleManager {
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern EXCEPTION_PREFIX_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+Exception: .+");
    private static final Pattern JAVA_PREFIX_PATTERN = Pattern.compile("^java\\.[a-zA-Z0-9_.]+: .+");
    private static final Pattern LOCALE_PATH_PATTERN = Pattern.compile(".*\\.[a-z_]+\\.[a-z_]+.*");
    private static final Map<Character, String> LEGACY_TO_MINIMESSAGE = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"), Map.entry('r', "<reset>")
    );

    @Getter private Map<String, Object> messages;
    private Map<String, Object> configValues;
    private final String currentLocale;
    @Setter private MessageRenderer renderer;

    public LocaleManager(String locale) {
        this.currentLocale = locale;
        this.messages = new HashMap<>();
        this.configValues = new HashMap<>();
        loadLocale(locale);
    }

    public LocaleManager() {
        this("en_US");
    }

    public void setConfigValues(Map<String, Object> config) {
        this.configValues = config != null ? config : new HashMap<>();
    }

    private void loadLocale(String locale) {
        String resourcePath = "/locale/" + locale + ".yml";
        try (InputStream resourceStream = getClass().getResourceAsStream(resourcePath)) {
            if (resourceStream == null) throw new RuntimeException("Locale file not found: " + resourcePath);
            Yaml yaml = new Yaml();
            this.messages = yaml.load(resourceStream);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load locale: " + locale, e);
        }
    }

    public void loadFromFile(Path localeFile) {
        try {
            if (Files.exists(localeFile)) {
                try (InputStream is = Files.newInputStream(localeFile)) {
                    Yaml yaml = new Yaml();
                    Map<String, Object> fileMessages = yaml.load(is);
                    if (fileMessages != null) this.messages = YamlMergeUtil.deepMerge(this.messages, fileMessages);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load locale from file: " + localeFile, e);
        }
    }

    public String getMessage(String path) {
        return getMessage(path, Map.of());
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        Object value;
        if (path.startsWith("config.")) {
            String configPath = path.substring(7);
            value = getNestedValue(configValues, configPath);
            if (value == null) value = getNestedValue(messages, path);
        } else {
            value = getNestedValue(messages, path);
        }

        if (value instanceof String message) {
            for (Map.Entry<String, String> entry : placeholders.entrySet())
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());

            return colorize(message);
        }
        return "&cMissing locale: " + path;
    }

    @SuppressWarnings("unchecked")
    public List<String> getMessageList(String path) {
        return getMessageList(path, Map.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> getMessageList(String path, Map<String, String> placeholders) {
        Object value = getNestedValue(messages, path);
        if (value instanceof List) {
            List<String> list = (List<String>) value;
            return list.stream()
                    .map(line -> replacePlaceholders(line, placeholders))
                    .toList();
        }
        return List.of("&cMissing locale list: " + path);
    }

    private String resolveAsJoinedLines(String path, Map<String, String> variables) {
        Object value = getNestedValue(messages, path);
        if (value instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) value;
            return lines.stream()
                    .map(line -> replacePlaceholders(line, variables))
                    .collect(Collectors.joining("\n"));
        }
        if (value instanceof String) return getMessage(path, variables);
        return null;
    }

    private Object getNestedValue(Map<String, Object> map, String path) {
        String[] keys = path.split("\\.");
        Object current = map;

        for (String key : keys) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> currentMap = (Map<String, Object>) current;
                current = currentMap.get(key);
            } else return null;
        }

        return current;
    }

    private String replacePlaceholders(String line, Map<String, String> variables) {
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            line = line.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return colorize(line);
    }

    private String colorize(String message) {
        if (renderer != null && MessageRenderer.isMiniMessage(message)) {
            message = legacyToMiniMessage(message);
            return renderer.componentToLegacy(renderer.render(message));
        }
        return message.replace("&", "\u00a7");
    }

    private String legacyToMiniMessage(String message) {
        Matcher matcher = LEGACY_CODE_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement = LEGACY_TO_MINIMESSAGE.get(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String getPunishmentMessage(String path, Map<String, String> variables) {
        Map<String, String> allVariables = new HashMap<>(variables);
        allVariables.putIfAbsent("default_reason", getMessage("config.default_reason"));

        return getMessage(path, allVariables);
    }

    public PunishmentMessageBuilder punishment() {
        return new PunishmentMessageBuilder(this);
    }

    public String getPublicNotificationMessage(int ordinal, Map<String, String> variables) {
        String path = "punishment_types.ordinal_" + ordinal + ".public_notification";
        Object value = getNestedValue(messages, path);

        if (value instanceof String) return getMessage(path, variables);
        return getDefaultPublicNotification(ordinal, variables);
    }

    public String getPlayerNotificationMessage(int ordinal, String punishmentType, Map<String, String> baseVariables, SimplePunishment punishment, PunishmentMessages.MessageContext context) {
        Map<String, String> variables = new HashMap<>(baseVariables);
        variables.put("tense", getTenseForContext(context));
        variables.put("tense2", getTense2ForContext(context));
        variables.put("temp", punishment.isPermanent() ? "permanently" : "temporarily");
        variables.put("duration_formatted", getDurationFormatted(punishment, getPunishmentTypeName(punishmentType)));
        variables.put("mute_duration_formatted", getMuteDurationFormatted(punishment));

        variables.put("issued", formatIssuedDate(punishment));
        String playerDesc = punishment.getPlayerDescription();
        variables.put("player_description", playerDesc != null ? playerDesc : "");

        String issuer = punishment.getIssuerName();
        variables.put("issuer", issuer != null ? issuer : "Staff");

        variables.put("will_expire", getWillExpireMessage(punishment));
        String category = punishment.getCategory();
        return getPlayerNotificationMessageWithCategory(ordinal, punishmentType, variables);
    }

    private String formatIssuedDate(SimplePunishment punishment) {
        java.util.Date issuedDate = punishment.getIssuedAsDate();
        return formatDate(issuedDate);
    }

    private String getWillExpireMessage(SimplePunishment punishment) {
        if (punishment.isPermanent()) return "";

        Long expiration = punishment.getExpiration();
        if (expiration == null) return "";

        long timeLeft = expiration - System.currentTimeMillis();
        if (timeLeft <= 0) return "";

        String duration = PunishmentMessages.formatDuration(timeLeft);
        String punishmentTypeWord = punishment.isBan() ? "ban" : (punishment.isMute() ? "mute" : "punishment");

        return "\n&7This " + punishmentTypeWord + " will expire in &f" + duration + "&7.";
    }

    private String getPlayerNotificationMessageWithCategory(int ordinal, String category, Map<String, String> variables) {
        String path = "punishment_types.ordinal_" + ordinal + ".player_notification";
        String result = resolveAsJoinedLines(path, variables);
        return result != null ? result : getDefaultPlayerNotificationByCategory(category, variables);
    }

    private String getDefaultPlayerNotificationByCategory(String category, Map<String, String> variables) {
        String defaultPath = getDefaultPlayerPathForCategory(category);
        String result = resolveAsJoinedLines(defaultPath, variables);
        return result != null ? result : getMessage("punishments.player_notifications.default", variables);
    }

    private String getDefaultPlayerPathForCategory(String category) {
        if (category == null) return "punishments.player_notifications.default";

        return switch (category.toUpperCase()) {
            case "KICK" -> "punishments.player_notifications.kick_default";
            case "MUTE" -> "punishments.player_notifications.mute_default";
            case "BAN" -> "punishments.player_notifications.ban_default";
            default -> "punishments.player_notifications.default";
        };
    }

    private String getDefaultPublicNotification(int ordinal, Map<String, String> variables) {
        String defaultPath = getDefaultPublicPathForOrdinal(ordinal);
        return getMessage(defaultPath, variables);
    }

    private String getDefaultPublicPathForOrdinal(int ordinal) {
        return "punishments.public_notification." + suffixForOrdinal(ordinal);
    }

    private static String suffixForOrdinal(int ordinal) {
        return switch (ordinal) {
            case 0 -> "kick_default";
            case 1 -> "mute_default";
            default -> ordinal >= 2 ? "ban_default" : "default";
        };
    }

    private String getTenseForContext(PunishmentMessages.MessageContext context) {
        return switch (context) {
            case SYNC, CHAT -> "are";
            default -> "have been";
        };
    }

    private String getTense2ForContext(PunishmentMessages.MessageContext context) {
        return switch (context) {
            case SYNC, CHAT -> "is";
            default -> "has been";
        };
    }

    private String getDurationFormatted(SimplePunishment punishment, String punishmentType) {
        if (punishment.isPermanent()) return "";

        long timeLeft = punishment.getExpiration() - System.currentTimeMillis();
        if (timeLeft <= 0) return "";

        String duration = PunishmentMessages.formatDuration(timeLeft);
        return "\n&7This " + punishmentType + " will expire in " + duration;
    }

    private String getMuteDurationFormatted(SimplePunishment punishment) {
        if (punishment.isPermanent()) return "";

        long timeLeft = punishment.getExpiration() - System.currentTimeMillis();
        if (timeLeft <= 0) return "";

        String duration = PunishmentMessages.formatDuration(timeLeft);
        return " for " + duration;
    }

    private String getPunishmentTypeName(String punishmentType) {
        if (punishmentType == null) return "punishment";
        return punishmentType.toLowerCase();
    }

    public String formatDuration(long durationMs) {
        if (durationMs <= 0) return getMessage("config.duration_format.permanent");

        long days = TimeUnit.MILLISECONDS.toDays(durationMs);
        long hours = TimeUnit.MILLISECONDS.toHours(durationMs) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;

        StringBuilder duration = new StringBuilder();

        if (days > 0) duration.append(getMessage("config.duration_format.days", Map.of("days", String.valueOf(days))));
        if (hours > 0) {
            if (!duration.isEmpty()) duration.append(" ");
            duration.append(getMessage("config.duration_format.hours", Map.of("hours", String.valueOf(hours))));
        }
        if (minutes > 0) {
            if (!duration.isEmpty()) duration.append(" ");
            duration.append(getMessage("config.duration_format.minutes", Map.of("minutes", String.valueOf(minutes))));
        }
        if (seconds > 0 && duration.isEmpty()) {
            duration.append(getMessage("config.duration_format.seconds", Map.of("seconds", String.valueOf(seconds))));
        }

        return duration.toString();
    }

    public String formatDate(Date date) {
        if (date == null) return "Unknown";
        String format = getMessage("config.date_format");
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(format);
            String tz = getMessage("config.timezone");
            if (tz != null && !tz.isEmpty() && !tz.startsWith("\u00a7cMissing")) dateFormat.setTimeZone(java.util.TimeZone.getTimeZone(tz));
            return dateFormat.format(date);
        } catch (Exception e) {
            return date.toString();
        }
    }

    public String getDateFormatPattern() {
        return getMessage("config.date_format");
    }

    public static class PunishmentMessageBuilder {
        private final LocaleManager localeManager;
        private final Map<String, String> variables;

        public PunishmentMessageBuilder(LocaleManager localeManager) {
            this.localeManager = localeManager;
            this.variables = new HashMap<>();
        }

        public PunishmentMessageBuilder target(String target) {
            variables.put("target", target);
            return this;
        }

        public PunishmentMessageBuilder type(String type) {
            variables.put("type", type);
            variables.put("punishment_type", type);
            return this;
        }

        public PunishmentMessageBuilder duration(long durationMs) {
            variables.put("duration", localeManager.formatDuration(durationMs));
            variables.put("for_duration", " for " +localeManager.formatDuration(durationMs));
            return this;
        }

        public PunishmentMessageBuilder punishmentId(String punishmentId) {
            variables.put("id", punishmentId);
            return this;
        }

        public String get(String path) {
            return localeManager.getPunishmentMessage(path, variables);
        }

    }

    public void reloadLocale() {
        loadLocale(currentLocale);
    }

    public String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) return getMessage("messages.unknown_error");

        String unwrapped = errorMessage;
        while (EXCEPTION_PREFIX_PATTERN.matcher(unwrapped).matches()) {
            unwrapped = unwrapped.substring(unwrapped.indexOf(": ") + 2);
        }
        while (JAVA_PREFIX_PATTERN.matcher(unwrapped).matches()) {
            unwrapped = unwrapped.substring(unwrapped.indexOf(": ") + 2);
        }

        if (unwrapped.equalsIgnoreCase("Player not found")) return getMessage("general.player_not_found");
        if (unwrapped.contains("Missing locale:") || LOCALE_PATH_PATTERN.matcher(unwrapped).matches()) return getMessage("messages.unknown_error");

        String cleaned = unwrapped.replaceAll("[\u00a7&][0-9a-fk-or]", "");
        if (cleaned.contains("Exception") || cleaned.contains("java.") || cleaned.contains("null")) return getMessage("messages.unknown_error");

        return cleaned;
    }
}
