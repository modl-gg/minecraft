package gg.modl.minecraft.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ModlFabricMod implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        String gameVersion = FabricLoader.getInstance()
                .getModContainer("minecraft").get()
                .getMetadata().getVersion().getFriendlyString();

        String implClass = gameVersion.startsWith("26.")
                ? "gg.modl.minecraft.fabric.v26.ModlFabricModImpl"
                : "gg.modl.minecraft.fabric.v1_21.ModlFabricModImpl";

        try {
            DedicatedServerModInitializer impl = (DedicatedServerModInitializer)
                    Class.forName(implClass).getConstructor().newInstance();
            impl.onInitializeServer();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load modl Fabric implementation for MC " + gameVersion, e);
        }
    }
}
