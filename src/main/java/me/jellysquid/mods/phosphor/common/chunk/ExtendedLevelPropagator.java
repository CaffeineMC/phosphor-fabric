package me.jellysquid.mods.phosphor.common.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;

public interface ExtendedLevelPropagator {
    /**
     * Mimics {@link net.minecraft.world.chunk.light.LevelPropagator#remove(long)}, but does not modify the per-chunk
     * cache.
     */
    void bridge$removeOnUnload(long pos);

    /**
     * Removes all entries in idToLevel belonging to the specified chunk.
     */
    LongSet removeIdToLevelByChunk(long pos);
}
