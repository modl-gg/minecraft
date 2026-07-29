package gg.modl.minecraft.fabric.v26.handler;

import lombok.Value;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

@Value
class PlayerSnapshot {
    ItemStack[] inventoryContents;
    ResourceKey<Level> dimension;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    GameType gameMode;
    float health;
    int foodLevel;
    float exp;
    int level;
}
