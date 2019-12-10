package me.jellysquid.mods.phosphor.common.util.math;

import net.minecraft.util.math.Direction;

public class DirectionHelper {
    /**
     * Benchmarks show this to be roughly ~50% faster than {@link Direction#fromVector(int, int, int)}. Input vector
     * is not required to be normalized, which also shaves off some CPU overhead.
     *
     * @param x The x component of the vector
     * @param y The y component of the vector
     * @param z The z component of the vector
     * @return The direction in block space which the vector represents
     * @throws IllegalArgumentException If the vector doesn't represent a valid direction
     */
    public static Direction getVecDirection(int x, int y, int z) {
        if (x == 0 && y < 0 && z == 0) {
            return Direction.DOWN;
        } else if (x == 0 && y > 0 && z == 0) {
            return Direction.UP;
        } else if (x == 0 && y == 0 && z < 0) {
            return Direction.NORTH;
        } else if (x == 0 && y == 0 && z > 0) {
            return Direction.SOUTH;
        } else if (x < 0 && y == 0 && z == 0) {
            return Direction.WEST;
        } else if (x > 0 && y == 0 && z == 0) {
            return Direction.EAST;
        } else {
            return null;
        }
    }
}
