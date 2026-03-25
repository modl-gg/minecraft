package io.github._4drian3d.signedvelocity.shared.logger;

import io.github._4drian3d.signedvelocity.shared.PropertyHolder;
import org.slf4j.Logger;

import java.util.function.Supplier;

public interface DebugLogger {
    boolean DEBUG = PropertyHolder.readBoolean("io.github._4drian3d.signedvelocity.debug", false);

    void debugMultiple(Supplier<String[]> supplier);

    void debug(Supplier<String> supplier);

    class Slf4j implements DebugLogger {
        private final Logger logger;

        public Slf4j(Logger logger) {
            this.logger = logger;
        }

        public Logger logger() {
            return logger;
        }

        @Override
        public void debugMultiple(final Supplier<String[]> supplier) {
            if (DEBUG) {
                for (final String line : supplier.get()) {
                    logger.info("[DEBUG] {}", line);
                }
            }
        }

        @Override
        public void debug(Supplier<String> supplier) {
            if (DEBUG) {
                logger.info("[DEBUG] {}", supplier.get());
            }
        }
    }
}
