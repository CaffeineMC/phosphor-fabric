package me.jellysquid.mods.phosphor.common.util;

import net.minecraft.util.math.MathHelper;

public class BlockPosHelper {
    // [VanillaCopy] BlockPos static fields
    private static final int SIZE_BITS_X = 1 + MathHelper.log2(MathHelper.smallestEncompassingPowerOfTwo(30000000));
    private static final int SIZE_BITS_Z;
    private static final int SIZE_BITS_Y;

    private static final long BITS_Y;

    static {
        SIZE_BITS_Z = SIZE_BITS_X;
        SIZE_BITS_Y = 64 - SIZE_BITS_X - SIZE_BITS_Z;
        BITS_Y = (1L << SIZE_BITS_Y) - 1L;
        Y_MASK = ~BITS_Y;
    }

    private static final long Y_MASK;

    /**
     * Quicker than re-encoding an integer {@link net.minecraft.util.math.BlockPos} when you only need to update one coordinate.
     *
     * @param pos The integer position containing the old X/Z coordinate values
     * @param val The new y-coordinate to update {@param pos} with
     * @return A new integer BlockPos which is identical to BlockPos.asLong(pos.x, y, pos.z)
     */
    public static long updateYLong(long pos, int val) {
        return (pos & Y_MASK) | ((long) val & BITS_Y);
    }
}
