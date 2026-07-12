package gg.modl.minecraft.fabric.v26.handler;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

final class PlayerSnapshot {
    final ItemStack[] inventoryContents;
    final double x;
    final double y;
    final double z;
    final float yaw;
    final float pitch;
    final GameType gameMode;
    final float health;
    final int foodLevel;
    final float exp;
    final int level;

    PlayerSnapshot(ItemStack[] inventoryContents,
                   double x, double y, double z, float yaw, float pitch,
                   GameType gameMode, float health, int foodLevel, float exp, int level) {
        this.inventoryContents = inventoryContents;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.gameMode = gameMode;
        this.health = health;
        this.foodLevel = foodLevel;
        this.exp = exp;
        this.level = level;
    }
}
