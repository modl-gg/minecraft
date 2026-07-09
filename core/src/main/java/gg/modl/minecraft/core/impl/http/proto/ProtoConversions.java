package gg.modl.minecraft.core.impl.http.proto;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared proto&lt;-&gt;domain conversion helpers for the V3 plugin HTTP mappers.
 *
 * <p>Mirrors the {@code Struct}/{@code Value} handling in the backend {@code *ProtoMapper}
 * classes and the flexible timestamp parsing that the legacy V2 JSON client relied on.
 * All methods are pure and stateless.</p>
 */
public final class ProtoConversions {

    private ProtoConversions() {
    }

    /**
     * Converts a protobuf {@link Struct} into a plain {@code Map}, the shape the domain DTOs
     * expect for their free-form {@code data} fields.
     */
    public static Map<String, Object> structToMap(Struct struct) {
        Map<String, Object> result = new LinkedHashMap<>();
        struct.getFieldsMap().forEach((key, value) -> result.put(key, valueToObject(value)));
        return result;
    }

    /**
     * Converts a plain {@code Map} into a protobuf {@link Struct} for request payloads.
     */
    public static Struct mapToStruct(Map<String, Object> map) {
        Struct.Builder builder = Struct.newBuilder();
        map.forEach((key, value) -> builder.putFields(key, objectToValue(value)));
        return builder.build();
    }

    private static Object valueToObject(Value value) {
        switch (value.getKindCase()) {
            case NUMBER_VALUE:
                return value.getNumberValue();
            case STRING_VALUE:
                return value.getStringValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case STRUCT_VALUE:
                return structToMap(value.getStructValue());
            case LIST_VALUE:
                List<Object> list = new ArrayList<>();
                value.getListValue().getValuesList().forEach(item -> list.add(valueToObject(item)));
                return list;
            case NULL_VALUE:
            case KIND_NOT_SET:
            default:
                return null;
        }
    }

    private static Value objectToValue(Object object) {
        Value.Builder builder = Value.newBuilder();
        if (object == null) {
            return builder.setNullValue(NullValue.NULL_VALUE).build();
        }
        if (object instanceof String) {
            return builder.setStringValue((String) object).build();
        }
        if (object instanceof Number) {
            return builder.setNumberValue(((Number) object).doubleValue()).build();
        }
        if (object instanceof Boolean) {
            return builder.setBoolValue((Boolean) object).build();
        }
        if (object instanceof Map<?, ?>) {
            Struct.Builder struct = Struct.newBuilder();
            ((Map<?, ?>) object).forEach((key, value) -> struct.putFields(Objects.toString(key), objectToValue(value)));
            return builder.setStructValue(struct).build();
        }
        if (object instanceof Iterable<?>) {
            ListValue.Builder list = ListValue.newBuilder();
            ((Iterable<?>) object).forEach(item -> list.addValues(objectToValue(item)));
            return builder.setListValue(list).build();
        }
        return builder.setStringValue(Objects.toString(object)).build();
    }

    /**
     * Parses a date that the backend serialized as an RFC3339/ISO string or epoch-millis string.
     * Mirrors the flexible parsing the V2 JSON client used. Returns {@code null} for empty input.
     */
    public static Date parseDate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        try {
            return Date.from(Instant.parse(trimmed));
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return Date.from(OffsetDateTime.parse(trimmed).toInstant());
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return new Date(Long.parseLong(trimmed));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Converts an epoch-millis value to a {@link Date}, treating {@code 0} as absent.
     */
    public static Date dateFromMillis(long millis) {
        return millis > 0 ? new Date(millis) : null;
    }

    /**
     * Returns {@code null} for empty strings, otherwise the value. Mirrors the
     * "empty string = absent" convention the V3 controllers use for optional fields.
     */
    public static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
