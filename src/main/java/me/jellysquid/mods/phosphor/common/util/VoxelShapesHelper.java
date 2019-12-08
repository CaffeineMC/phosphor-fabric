package me.jellysquid.mods.phosphor.common.util;

import net.minecraft.util.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public class VoxelShapesHelper {
    /**
     * Includes additional early-returns for {@link VoxelShapes#method_20713(VoxelShape, VoxelShape)}.
     */
    // [VanillaCopy] VoxelShapes#method_20713(VoxelShape, VoxelShape)
    public static boolean method_20713_fast(VoxelShape a, VoxelShape b) {
        if (a == VoxelShapes.fullCube() || b == VoxelShapes.fullCube()) {
            return true;
        }

        // Check to see if the arguments are generic empty shapes, the calls below require dynamic dispatch and are
        // slower.
        if (a == VoxelShapes.empty() && b == VoxelShapes.empty()) {
            return false;
        }

        boolean ae = a.isEmpty();
        boolean be = b.isEmpty();

        if (ae && be) {
            return false;
        }

        // Don't waste time combining the shapes if one of them is empty
        if (ae || be) {
            return !VoxelShapes.matchesAnywhere(VoxelShapes.fullCube(), ae ? b : a, BooleanBiFunction.ONLY_FIRST);
        }

        return !VoxelShapes.matchesAnywhere(VoxelShapes.fullCube(), VoxelShapes.combine(a, b, BooleanBiFunction.OR), BooleanBiFunction.ONLY_FIRST);
    }

}
