package gg.modl.minecraft.core.boot;

import gg.modl.minecraft.core.util.PluginLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public interface ConsoleInput {

    String readLine(String prompt);

    default boolean confirm(String prompt) {
        String response = readLine(prompt + " [yes/no]: ");
        return response != null && (response.equalsIgnoreCase("yes") || response.equalsIgnoreCase("y"));
    }

    static ConsoleInput system(PluginLogger logger) {
        return new ConsoleInput() {
            private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            @Override
            public String readLine(String prompt) {
                logger.info(prompt);
                try {
                    return reader.readLine();
                } catch (IOException e) {
                    logger.warning("Failed to read console input: " + e.getMessage());
                    return null;
                }
            }
        };
    }
}
