package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.freeze.FreezeDrift;
import lombok.Value;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

@Value
class FreezeAnchor {
    UUID worldId;
    double x;
    double y;
    double z;

    static FreezeAnchor of(Location location) {
        return new FreezeAnchor(location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ());
    }

    boolean holds(Location location) {
        return inWorldOf(location)
                && !FreezeDrift.exceeded(location.getX() - x, location.getY() - y, location.getZ() - z);
    }

    boolean inWorldOf(Location location) {
        World world = location.getWorld();
        return world != null && world.getUID().equals(worldId);
    }

    Location toLocation(World world, float yaw, float pitch) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
