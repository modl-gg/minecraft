package gg.modl.minecraft.core.bridge.protocol;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BridgeProtocol {
    public static final int MAX_FRAME_LENGTH = 65536;
    public static final int LENGTH_FIELD_LENGTH = 4;

    private static final byte[] MAGIC = "modl".getBytes(StandardCharsets.US_ASCII);

    private BridgeProtocol() {
    }

    public static int magicLength() {
        return MAGIC.length;
    }

    public static void writeMagic(OutputStream out) throws IOException {
        out.write(MAGIC);
    }

    public static boolean matchesMagic(byte[] candidate) {
        return Arrays.equals(candidate, MAGIC);
    }

    public static byte[] encode(BridgeAction action, String... args) throws IOException {
        return encode(action.wire(), args);
    }

    public static byte[] encode(String action, String... args) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeUTF(action);
        for (String arg : args) {
            dos.writeUTF(arg != null ? arg : "");
        }
        dos.flush();
        return baos.toByteArray();
    }

    public static LengthFieldBasedFrameDecoder newFrameDecoder() {
        return new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_LENGTH, 0, LENGTH_FIELD_LENGTH);
    }

    public static LengthFieldPrepender newFramePrepender() {
        return new LengthFieldPrepender(LENGTH_FIELD_LENGTH);
    }
}
