package me.jellysquid.mods.phosphor.common.chunk;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public interface ExtendedBlockState {
    /**
     * @return True if the block state has a static shape, otherwise false if it doesn't.
     */
    boolean hasDynamicLightShape();

    /**
     * @return True if the block state has a special state it is represented by for lighting.
     */
    boolean hasSpecialLightShape();

    /**
     * @return The cached VoxelShape which represents the light volume in the specified direction.
     */
    VoxelShape getStaticLightShape(Direction dir);

    /**
     * Creates a new VoxelShape which represents the light volume for the block in the specified context. This
     * will not be cached.
     */
    VoxelShape getDynamicLightShape(BlockView view, BlockPos pos, Direction dir);

    /**
     * @return True if the block state dynamically adjusts its light opacity.
     */
    boolean hasDynamicLightOpacity();

    /**
     * @return The cached light opacity for this state.
     */
    int getStaticLightOpacity();

    /**
     * @return The dynamic light opacity for this state at the specified location in the world. This will not be
     * cached.
     */
    int getDynamicLightOpacity(BlockView view, BlockPos pos);
}
