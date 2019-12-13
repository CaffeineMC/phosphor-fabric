package me.jellysquid.mods.phosphor.common.chunk;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;

public interface ExtendedLightStorage<M extends ChunkToNibbleArrayMap<M>> extends ExtendedGenericLightStorage {
    /**
     * Bridge method to LightStorage#getDataForChunk(M, long).
     */
    ChunkNibbleArray bridge$getDataForChunk(M data, long chunk);

    /**
     * Bridge method to LightStorage#getStorageUncached().
     */
    M bridge$getStorageUncached();
}
