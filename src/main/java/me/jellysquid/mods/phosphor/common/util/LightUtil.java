package me.jellysquid.mods.phosphor.common.util;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Arrays;

public class LightUtil {
    public static final Direction[] DIRECTIONS = Direction.values();

    public static final VoxelShape[] DEFAULT_LIGHT_SHAPES = new VoxelShape[DIRECTIONS.length];

    static {
        Arrays.fill(DEFAULT_LIGHT_SHAPES, VoxelShapes.empty());
    }
}
