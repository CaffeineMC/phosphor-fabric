package me.jellysquid.mods.phosphor.common.chunk.light;

import me.jellysquid.mods.phosphor.common.util.collections.DoubleBufferedLong2ObjectHashMap;
import net.minecraft.world.chunk.ChunkNibbleArray;

public interface SharedBlockLightData {
    /**
     * Make this instance a copy of another. The shared copy cannot be directly written into.
     *
     * @param queue The queue of light updates
     */
    void makeSharedCopy(DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> queue);
}
