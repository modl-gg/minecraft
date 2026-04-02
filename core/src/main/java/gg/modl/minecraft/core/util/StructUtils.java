package gg.modl.minecraft.core.util;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class StructUtils {

    private StructUtils() {}

    public static Struct fromMap(Map<String, Object> map) {
        if (map == null) return Struct.getDefaultInstance();
        Struct.Builder builder = Struct.newBuilder();
        map.forEach((key, val) -> builder.putFields(key, toValue(val)));
        return builder.build();
    }

    public static Map<String, Object> toMap(Struct struct) {
        Map<String, Object> map = new HashMap<String, Object>();
        struct.getFieldsMap().forEach((key, val) -> map.put(key, fromValue(val)));
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Value toValue(Object obj) {
        if (obj == null) return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        if (obj instanceof String) return Value.newBuilder().setStringValue((String) obj).build();
        if (obj instanceof Number) return Value.newBuilder().setNumberValue(((Number) obj).doubleValue()).build();
        if (obj instanceof Boolean) return Value.newBuilder().setBoolValue((Boolean) obj).build();
        if (obj instanceof Map) return Value.newBuilder().setStructValue(fromMap((Map<String, Object>) obj)).build();
        if (obj instanceof List) {
            ListValue.Builder list = ListValue.newBuilder();
            ((List<?>) obj).forEach(item -> list.addValues(toValue(item)));
            return Value.newBuilder().setListValue(list).build();
        }
        return Value.newBuilder().setStringValue(obj.toString()).build();
    }

    private static Object fromValue(Value value) {
        switch (value.getKindCase()) {
            case NULL_VALUE: return null;
            case NUMBER_VALUE: return value.getNumberValue();
            case STRING_VALUE: return value.getStringValue();
            case BOOL_VALUE: return value.getBoolValue();
            case STRUCT_VALUE: return toMap(value.getStructValue());
            case LIST_VALUE:
                return value.getListValue().getValuesList().stream()
                        .map(StructUtils::fromValue)
                        .collect(Collectors.toList());
            default: return null;
        }
    }
}
