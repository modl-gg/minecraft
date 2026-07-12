package gg.modl.minecraft.fabric.v1_21_1.handler;

import lombok.Value;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

@Value
class PlayerSnapshot {
    ItemStack[] inventoryContents;
    ItemStack[] armorContents;
    ItemStack[] offHandContents;
    RegistryKey<World> dimension;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    GameMode gameMode;
    float health;
    int foodLevel;
    float exp;
    int level;
}
