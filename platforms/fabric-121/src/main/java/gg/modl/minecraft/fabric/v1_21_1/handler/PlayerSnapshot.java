package gg.modl.minecraft.fabric.v1_21_1.handler;

import net.minecraft.item.ItemStack;
import net.minecraft.world.GameMode;

final class PlayerSnapshot {
    final ItemStack[] inventoryContents;
    final ItemStack[] armorContents;
    final ItemStack[] offHandContents;
    final double x;
    final double y;
    final double z;
    final float yaw;
    final float pitch;
    final GameMode gameMode;
    final float health;
    final int foodLevel;
    final float exp;
    final int level;

    PlayerSnapshot(ItemStack[] inventoryContents, ItemStack[] armorContents, ItemStack[] offHandContents,
                   double x, double y, double z, float yaw, float pitch,
                   GameMode gameMode, float health, int foodLevel, float exp, int level) {
        this.inventoryContents = inventoryContents;
        this.armorContents = armorContents;
        this.offHandContents = offHandContents;
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
