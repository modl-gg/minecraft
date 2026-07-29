package gg.modl.minecraft.spigot.bridge.handler;

import lombok.Value;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

@Value
class PlayerSnapshot {
    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final Location location;
    private final GameMode gameMode;
    private final double health;
    private final int foodLevel;
    private final float exp;
    private final int level;
}
