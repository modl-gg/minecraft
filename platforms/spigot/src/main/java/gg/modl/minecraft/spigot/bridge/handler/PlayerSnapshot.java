package gg.modl.minecraft.spigot.bridge.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

@Getter
@AllArgsConstructor
final class PlayerSnapshot {
    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final Location location;
    private final GameMode gameMode;
    private final double health;
    private final int foodLevel;
    private final float exp;
    private final int level;
}
