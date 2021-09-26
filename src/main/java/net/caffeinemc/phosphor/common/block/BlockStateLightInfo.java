package net.caffeinemc.phosphor.common.block;

import net.minecraft.util.shape.VoxelShape;

public interface BlockStateLightInfo {
    VoxelShape[] getExtrudedFaces();

    int getLightSubtracted();
}
