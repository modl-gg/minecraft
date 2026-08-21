package gg.modl.minecraft.bridge;

import java.util.Collections;
import java.util.List;

public final class BridgeReloadResult {
    private final List<String> failures;
    private final List<String> restartRequired;

    BridgeReloadResult(List<String> failures, List<String> restartRequired) {
        this.failures = Collections.unmodifiableList(failures);
        this.restartRequired = Collections.unmodifiableList(restartRequired);
    }

    public boolean isSuccessful() {
        return failures.isEmpty();
    }

    public List<String> getFailures() {
        return failures;
    }

    public List<String> getRestartRequired() {
        return restartRequired;
    }

    public boolean needsRestart() {
        return !restartRequired.isEmpty();
    }

    public String describeFailures() {
        return String.join(", ", failures);
    }

    public String describeRestartRequired() {
        return String.join(", ", restartRequired);
    }
}
