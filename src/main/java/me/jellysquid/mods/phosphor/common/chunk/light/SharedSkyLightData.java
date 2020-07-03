package me.jellysquid.mods.phosphor.common.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.jellysquid.mods.phosphor.common.util.collections.DoubleBufferedLong2IntHashMap;

public interface SharedSkyLightData {
    /**
     * Make this instance a copy of another, copying the object references from another instance into this one.
     *
     * @param map The sync-view of the {@param queue}
     * @param queue The queue of light updates
     */
    void makeSharedCopy(Long2IntOpenHashMap map, DoubleBufferedLong2IntHashMap queue);
}
