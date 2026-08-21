package gg.modl.minecraft.core.config.yaml;

public final class ManagedConfig {
    private final String fileName;
    private final ConfigSchema schema;

    private ManagedConfig(String fileName, ConfigSchema schema) {
        this.fileName = fileName;
        this.schema = schema;
    }

    public static ManagedConfig of(String fileName, ConfigSchema schema) {
        return new ManagedConfig(fileName, schema);
    }

    public String getFileName() {
        return fileName;
    }

    public String getResourcePath() {
        return "/" + fileName;
    }

    public ConfigSchema getSchema() {
        return schema;
    }
}
