package gg.modl.minecraft.core.util;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

public final class ProtoJsonUtils {

    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();

    private ProtoJsonUtils() {}

    public static String toJson(Message message) {
        try {
            return PRINTER.print(message);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to serialize proto to JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T fromJson(String json, Message.Builder builder) {
        try {
            PARSER.merge(json, builder);
            return (T) builder.build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to parse JSON to proto", e);
        }
    }
}
