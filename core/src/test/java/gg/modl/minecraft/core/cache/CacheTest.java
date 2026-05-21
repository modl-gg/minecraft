package gg.modl.minecraft.core.cache;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheTest {

    @Test
    void cachedStaffPermissionsAreNotChangedBySourceListMutation() {
        Cache cache = new Cache(new CachedProfileRegistry());
        UUID playerUuid = UUID.randomUUID();
        List<String> permissions = new ArrayList<>();
        permissions.add("modl.reports");

        cache.cacheStaffPermissions(playerUuid, "staff", "staff-id", "Moderator", permissions);
        permissions.clear();

        assertTrue(cache.hasPermission(playerUuid, "modl.reports.create"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnedPermissionListCannotMutateCache() throws Exception {
        Cache cache = new Cache(new CachedProfileRegistry());
        UUID playerUuid = UUID.randomUUID();
        List<String> sourcePermissions = new ArrayList<>();
        sourcePermissions.add("modl.reports");

        cache.cacheStaffPermissions(playerUuid, "staff", "staff-id", "Moderator", sourcePermissions);

        // No public accessor exists for the cached permission list; reflect to verify
        // the production wrapping (Collections.unmodifiableList) prevents mutation.
        Field cacheField = Cache.class.getDeclaredField("staffPermissionsCache");
        cacheField.setAccessible(true);
        Map<UUID, Cache.StaffPermissions> staffPermissionsCache =
                (Map<UUID, Cache.StaffPermissions>) cacheField.get(cache);
        List<String> cachedPermissions = staffPermissionsCache.get(playerUuid).getPermissions();

        assertThrows(UnsupportedOperationException.class, () -> cachedPermissions.add("modl.admin"));
        assertThrows(UnsupportedOperationException.class, () -> cachedPermissions.remove(0));
        assertThrows(UnsupportedOperationException.class, cachedPermissions::clear);
    }
}
