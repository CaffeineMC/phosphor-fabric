package me.jellysquid.mods.phosphor.common.chunk;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

public interface ExtendedMixinChunkLightProvider {
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
     * {@link ExtendedMixinChunkLightProvider#getBlockStateForLighting(int, int, int)}.
     */
    VoxelShape getVoxelShape(BlockState state, int x, int y, int z, Direction dir);


    /**
     * Returns the VoxelShape of a block for lighting. This will call
     * {@link ExtendedMixinChunkLightProvider#getBlockStateForLighting(int, int, int)} to retrieve the block state
     * at the specified coordinates. You should prefer the variant of this method which consumes a BlockState if you
     * already have obtained it prior as it will be faster.
     */
    VoxelShape getVoxelShape(int x, int y, int z, Direction dir);
}
