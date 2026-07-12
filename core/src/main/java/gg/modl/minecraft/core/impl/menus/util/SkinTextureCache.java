package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import gg.modl.minecraft.core.PluginServices;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;

import java.util.UUID;

public final class SkinTextureCache {
    private SkinTextureCache() {}

    public static void ensureCached(UUID uuid) {
        Cache cache = PluginServices.cache();
        if (uuid == null || cache == null) return;
        if (cache.getSkinTexture(uuid) != null) return;
        fetchAndCache(cache, uuid);
    }

    public static CirrusItem applyCached(CirrusItem item, UUID uuid) {
        Cache cache = PluginServices.cache();
        if (uuid == null || cache == null) return item;
        String texture = cache.getSkinTexture(uuid);
        return texture != null ? item.texture(texture) : item;
    }

    public static CirrusItem applyCachedOrFetch(CirrusItem item, UUID uuid) {
        Cache cache = PluginServices.cache();
        if (uuid == null || cache == null) return item;
        String texture = cache.getSkinTexture(uuid);
        if (texture != null) return item.texture(texture);
        fetchAndCache(cache, uuid);
        return item;
    }

    private static void fetchAndCache(Cache cache, UUID uuid) {
        MojangProfiles.client().get(uuid).thenAccept(wp -> {
            if (wp != null && wp.isValid() && wp.getTextureValue() != null) {
                cache.cacheSkinTexture(uuid, wp.getTextureValue());
            }
        });
    }
}
