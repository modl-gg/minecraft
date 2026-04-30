package gg.modl.minecraft.spigot.bridge;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeComponentTest {
    public interface ModernBlockAccess {
        Object getBlockData();
    }

    @Test
    void resolveBlockStateIdUsesModernBlockDataWhenAvailable() {
        Block block = mock(Block.class, Mockito.withSettings().extraInterfaces(ModernBlockAccess.class));
        Object blockData = new Object();
        AtomicBoolean legacyUsed = new AtomicBoolean(false);

        when(((ModernBlockAccess) block).getBlockData()).thenReturn(blockData);

        int stateId = BridgeComponent.resolveBlockStateId(
                block,
                data -> {
                    assertEquals(blockData, data);
                    return 1234;
                },
                (material, data) -> {
                    legacyUsed.set(true);
                    return -1;
                });

        assertEquals(1234, stateId);
        verify(((ModernBlockAccess) block)).getBlockData();
        verify(block, never()).getType();
        verify(block, never()).getData();
        assertEquals(false, legacyUsed.get());
    }

    @Test
    void resolveBlockStateIdFallsBackToLegacyWhenModernApiIsMissing() {
        Block block = mock(Block.class);

        when(block.getType()).thenReturn(Material.STONE);
        when(block.getData()).thenReturn((byte) 5);

        int stateId = BridgeComponent.resolveBlockStateId(
                block,
                data -> -1,
                (material, data) -> material == Material.STONE && data == (byte) 5 ? 77 : -1);

        assertEquals(77, stateId);
        verify(block).getType();
        verify(block).getData();
    }
}
