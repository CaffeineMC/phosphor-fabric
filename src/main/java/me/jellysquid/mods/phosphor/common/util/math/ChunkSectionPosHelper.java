package me.jellysquid.mods.phosphor.common.util.math;

import net.minecraft.util.math.ChunkSectionPos;

public class ChunkSectionPosHelper {
    /**
     * Quicker than re-encoding an integer {@link ChunkSectionPos} when you only need to update one coordinate.
     *
     * @param pos The integer position containing the old X/Z coordinate values
     * @param y   The new y-coordinate to update {@param pos} with
     * @return A new integer ChunkSectionPos which is identical to ChunkSectionPos.asLong(pos.x, y, pos.z)
     */
    public static long updateYLong(long pos, int y) {
        // [VanillaCopy] ChunkSectionPos static fields
        return (pos & ~0xFFFFF) | ((long) y & 0xFFFFF);
    }
}
