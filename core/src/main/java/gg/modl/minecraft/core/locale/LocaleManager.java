package gg.modl.minecraft.core.locale;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.core.util.TimeUtil;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;
import java.util.TimeZone;

public class LocaleManager {
    private static final Pattern EXCEPTION_PREFIX_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+Exception: .+");
    private static final Pattern JAVA_PREFIX_PATTERN = Pattern.compile("^java\\.[a-zA-Z0-9_.]+: .+");
    private static final Pattern LOCALE_PATH_PATTERN = Pattern.compile(".*\\.[a-z_]+\\.[a-z_]+.*");

    private static final String MISSING_MESSAGE_PREFIX = "&cMissing locale: ";
    private static final String MISSING_LIST_PREFIX = "&cMissing locale list: ";

    private static final String WILL_EXPIRE_FALLBACK = "\n&7This {type} will expire in &f{duration}&7.";
    private static final String DURATION_FORMATTED_FALLBACK = "\n&7This {type} will expire in {duration}";
    private static final String MUTE_DURATION_FORMATTED_FALLBACK = " for {duration}";

    private Map<String, Object> messages;
    private Map<String, Object> configValues;
    private final String currentLocale;
    private final YamlMessageResolver resolver;
    @Setter private MessageRenderer renderer;

    public LocaleManager(String locale) {
        this.currentLocale = locale;
        this.messages = new HashMap<>();
        this.configValues = new HashMap<>();
        this.resolver = new YamlMessageResolver(this::colorize);
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
        return getMessage(path, mapOf());
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        Object value = resolveValue(path);
        if (value instanceof String) return resolver.render((String) value, placeholders);
        return MISSING_MESSAGE_PREFIX + path;
    }

    public boolean hasMessage(String path) {
        return resolveValue(path) instanceof String;
    }

    private Object resolveValue(String path) {
        if (path.startsWith("config.")) {
            Object value = resolver.lookup(configValues, path.substring(7));
            return value != null ? value : resolver.lookup(messages, path);
        }
        return resolver.lookup(messages, path);
    }

    @SuppressWarnings("unchecked")
    public List<String> getMessageList(String path) {
        return getMessageList(path, mapOf());
    }

    @SuppressWarnings("unchecked")
    public List<String> getMessageList(String path, Map<String, String> placeholders) {
        Object value = resolver.lookup(messages, path);
        if (value instanceof List) {
            List<String> list = (List<String>) value;
            return list.stream()
                    .map(line -> resolver.render(line, placeholders))
                    .collect(Collectors.toList());
        }
        return listOf(MISSING_LIST_PREFIX + path);
    }

    private String resolveAsJoinedLines(String path, Map<String, String> variables) {
        Object value = resolver.lookup(messages, path);
        if (value instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) value;
            return lines.stream()
                    .map(line -> resolver.render(line, variables))
                    .collect(Collectors.joining("\n"));
        }
        if (value instanceof String) return getMessage(path, variables);
        return null;
    }

    private String colorize(String message) {
        if (renderer != null && MessageRenderer.isMiniMessage(message)) return renderer.renderToLegacy(message);
        return LegacyTextRenderer.colorize(message);
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
        Object value = resolver.lookup(messages, path);

        if (value instanceof String) return getMessage(path, variables);
        return getDefaultPublicNotification(ordinal, variables);
    }

    public String getPlayerNotificationMessage(int ordinal, String punishmentType, Map<String, String> baseVariables, SimplePunishment punishment, PunishmentMessageContext context) {
        Map<String, String> variables = new HashMap<>(baseVariables);
        variables.put("tense", getTenseForContext(context));
        variables.put("tense2", getTense2ForContext(context));
        variables.put("temp", punishment.isPermanent() ? getTempPermanent() : getTempTemporary());
        variables.put("duration_formatted", getDurationFormatted(punishment, getPunishmentTypeName(punishmentType)));
        variables.put("mute_duration_formatted", getMuteDurationFormatted(punishment));

        variables.put("issued", formatIssuedDate(punishment));
        String playerDesc = punishment.getPlayerDescription();
        variables.put("player_description", playerDesc != null ? playerDesc : "");

        String issuer = punishment.getIssuerName();
        variables.put("issuer", issuer != null ? issuer : "Staff");

        variables.put("will_expire", getWillExpireMessage(punishment));
        return getPlayerNotificationMessageWithCategory(ordinal, punishmentType, variables);
    }

    private String formatIssuedDate(SimplePunishment punishment) {
        Date issuedDate = punishment.getIssuedAsDate();
        return formatDate(issuedDate);
    }

    private String getWillExpireMessage(SimplePunishment punishment) {
        long timeLeft = remainingMillis(punishment);
        if (timeLeft <= 0) return "";

        String punishmentTypeWord = punishment.isBan() ? "ban" : (punishment.isMute() ? "mute" : "punishment");
        return formatExpiryTemplate("punishment_words.will_expire", WILL_EXPIRE_FALLBACK, punishmentTypeWord, timeLeft);
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

        String upper = category.toUpperCase();
        if ("KICK".equals(upper)) return "punishments.player_notifications.kick_default";
        if ("MUTE".equals(upper)) return "punishments.player_notifications.mute_default";
        if ("BAN".equals(upper)) return "punishments.player_notifications.ban_default";
        return "punishments.player_notifications.default";
    }

    private String getDefaultPublicNotification(int ordinal, Map<String, String> variables) {
        String defaultPath = getDefaultPublicPathForOrdinal(ordinal);
        return getMessage(defaultPath, variables);
    }

    private String getDefaultPublicPathForOrdinal(int ordinal) {
        return "punishments.public_notification." + suffixForOrdinal(ordinal);
    }

    private static String suffixForOrdinal(int ordinal) {
        if (ordinal == 0) return "kick_default";
        if (ordinal == 1) return "mute_default";
        return ordinal >= 2 ? "ban_default" : "default";
    }

    private String getTenseForContext(PunishmentMessageContext context) {
        if (context == PunishmentMessageContext.SYNC || context == PunishmentMessageContext.CHAT) {
            return getRawMessage("punishment_words.tense_active", "are");
        }
        return getRawMessage("punishment_words.tense_default", "have been");
    }

    private String getTense2ForContext(PunishmentMessageContext context) {
        if (context == PunishmentMessageContext.SYNC || context == PunishmentMessageContext.CHAT) {
            return getRawMessage("punishment_words.tense2_active", "is");
        }
        return getRawMessage("punishment_words.tense2_default", "has been");
    }

    private String getTempTemporary() {
        return getRawMessage("punishment_words.temporarily", "temporarily");
    }

    private String getTempPermanent() {
        return getRawMessage("punishment_words.permanently", "permanently");
    }

    private String getRawMessage(String path, String fallback) {
        Object value = resolver.lookup(messages, path);
        return value instanceof String ? (String) value : fallback;
    }

    private String formatExpiryTemplate(String path, String fallback, String type, long timeLeftMillis) {
        Map<String, String> variables = new HashMap<>();
        if (type != null) variables.put("type", type);
        variables.put("duration", TimeUtil.formatTimeMillis(timeLeftMillis));
        return resolver.applyPlaceholders(getRawMessage(path, fallback), variables);
    }

    private long remainingMillis(SimplePunishment punishment) {
        if (punishment.isPermanent()) return 0;
        Long expiration = punishment.getExpiration();
        if (expiration == null) return 0;
        return expiration - System.currentTimeMillis();
    }

    private String getDurationFormatted(SimplePunishment punishment, String punishmentType) {
        long timeLeft = remainingMillis(punishment);
        if (timeLeft <= 0) return "";
        return formatExpiryTemplate("punishment_words.duration_formatted", DURATION_FORMATTED_FALLBACK, punishmentType, timeLeft);
    }

    private String getMuteDurationFormatted(SimplePunishment punishment) {
        long timeLeft = remainingMillis(punishment);
        if (timeLeft <= 0) return "";
        return formatExpiryTemplate("punishment_words.mute_duration_formatted", MUTE_DURATION_FORMATTED_FALLBACK, null, timeLeft);
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

        if (days > 0) duration.append(getMessage("config.duration_format.days", mapOf("days", String.valueOf(days))));
        if (hours > 0) {
            if (duration.length() > 0) duration.append(" ");
            duration.append(getMessage("config.duration_format.hours", mapOf("hours", String.valueOf(hours))));
        }
        if (minutes > 0) {
            if (duration.length() > 0) duration.append(" ");
            duration.append(getMessage("config.duration_format.minutes", mapOf("minutes", String.valueOf(minutes))));
        }
        if (seconds > 0 && duration.length() == 0) {
            duration.append(getMessage("config.duration_format.seconds", mapOf("seconds", String.valueOf(seconds))));
        }

        return duration.toString();
    }

    public String formatDate(Date date) {
        if (date == null) return "Unknown";
        String format = getMessage("config.date_format");
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(format);
            if (hasMessage("config.timezone")) {
                String tz = getMessage("config.timezone");
                if (tz != null && !tz.isEmpty()) dateFormat.setTimeZone(TimeZone.getTimeZone(tz));
            }
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
            variables.put("for_duration", "for " +localeManager.formatDuration(durationMs));
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
