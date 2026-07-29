package gg.modl.minecraft.core.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TempConfigs {
    private TempConfigs() {}

    public static void write(Path file, String... lines) throws IOException {
        Files.write(file, String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }
}
