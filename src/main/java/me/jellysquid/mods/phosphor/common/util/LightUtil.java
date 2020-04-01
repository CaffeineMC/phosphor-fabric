package me.jellysquid.mods.phosphor.common.util;

import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public class LightUtil {
    /**
     * Replacement for {@link VoxelShapes#unionCoversFullCube(VoxelShape, VoxelShape)}. This implementation early-exits
     * in some common situations to avoid unnecessary computation.
     *
     * @author JellySquid
     */
    public static boolean unionCoversFullCube(VoxelShape a, VoxelShape b) {
        // At least one shape is a full cube and will match
        if (a == VoxelShapes.fullCube() || b == VoxelShapes.fullCube()) {
            return true;
        }

        boolean ae = a == VoxelShapes.empty() || a.isEmpty();
        boolean be = b == VoxelShapes.empty() || b.isEmpty();

        // If both shapes are empty, they can never overlap
        if (ae && be) {
            return false;
        }

        // Test each shape individually if they're non-empty and fail fast
        return (ae || !VoxelShapes.matchesAnywhere(VoxelShapes.fullCube(), a, BooleanBiFunction.ONLY_FIRST)) &&
                (be || !VoxelShapes.matchesAnywhere(VoxelShapes.fullCube(), b, BooleanBiFunction.ONLY_FIRST));
    }
}
