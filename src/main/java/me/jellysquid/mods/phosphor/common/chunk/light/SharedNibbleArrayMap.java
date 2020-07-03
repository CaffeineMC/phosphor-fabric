package me.jellysquid.mods.phosphor.common.chunk.light;

import me.jellysquid.mods.phosphor.common.util.collections.DoubleBufferedLong2ObjectHashMap;
import net.minecraft.world.chunk.ChunkNibbleArray;

public interface SharedNibbleArrayMap {
    /**
     * Initializes the data for this extended chunk array map. This should only be called once with the initialization
     * of a subtype.
     * @throws IllegalStateException If the map has already been initialized
     */
    void init();

    /**
     * Makes this map a shared copy of another. The shared copy cannot be directly written into.
     */
    void makeSharedCopy(SharedNibbleArrayMap map);

    /**
     * Returns the queue of pending changes for this map.
     */
    DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> getUpdateQueue();
}
