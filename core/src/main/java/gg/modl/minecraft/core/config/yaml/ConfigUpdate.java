package gg.modl.minecraft.core.config.yaml;

import java.util.Collections;
import java.util.List;

public final class ConfigUpdate {
    private final boolean created;
    private final List<String> addedPaths;

    private ConfigUpdate(boolean created, List<String> addedPaths) {
        this.created = created;
        this.addedPaths = addedPaths;
    }

    static ConfigUpdate created() {
        return new ConfigUpdate(true, Collections.<String>emptyList());
    }

    static ConfigUpdate unchanged() {
        return new ConfigUpdate(false, Collections.<String>emptyList());
    }

    static ConfigUpdate added(List<String> addedPaths) {
        return new ConfigUpdate(false, Collections.unmodifiableList(addedPaths));
    }

    public boolean isCreated() {
        return created;
    }

    public List<String> getAddedPaths() {
        return addedPaths;
    }

    public boolean isChanged() {
        return created || !addedPaths.isEmpty();
    }
}
