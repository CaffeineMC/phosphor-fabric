package me.jellysquid.mods.phosphor.common.chunk.light;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

public interface LightProviderBlockAccess {
    /**
     * Returns the BlockState which represents the block at the specified coordinates in the world. This may return
     * a different BlockState than what actually exists at the coordinates (such as if it is out of bounds), but will
     * always represent a state with valid light properties for that coordinate.
     */
    BlockState getBlockStateForLighting(int x, int y, int z);

    /**
     * Returns the amount of light which is blocked at the specified coordinates by the BlockState.
     */
    int getSubtractedLight(BlockState state, int x, int y, int z);

    /**
     * Returns the VoxelShape of a block for lighting without making a second call to
     * {@link LightProviderBlockAccess#getBlockStateForLighting(int, int, int)}.
     */
    VoxelShape getOpaqueShape(BlockState state, int x, int y, int z, Direction dir);

}
