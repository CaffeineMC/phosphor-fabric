package me.jellysquid.mods.phosphor.common.block;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

public interface ShapeCacheAccess {
    VoxelShape[] getExtrudedFaces();

    int getLightSubtracted();

    boolean isOpaque();
}
