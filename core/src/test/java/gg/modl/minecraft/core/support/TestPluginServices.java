package gg.modl.minecraft.core.support;

import gg.modl.minecraft.core.PluginServices;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

public final class TestPluginServices {
    private TestPluginServices() {}

    public static PluginServices install(Cache cache, LocaleManager localeManager) {
        PluginServices services = new PluginServices(
                cache, localeManager, null, null, null, null, null, null);
        PluginServices.install(services);
        return services;
    }

    public static PluginServices install(Cache cache) {
        return install(cache, new LocaleManager());
    }
}
