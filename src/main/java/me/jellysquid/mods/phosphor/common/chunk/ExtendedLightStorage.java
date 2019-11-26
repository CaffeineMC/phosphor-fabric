package me.jellysquid.mods.phosphor.common.chunk;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.WorldNibbleStorage;

public interface ExtendedLightStorage<M extends WorldNibbleStorage<M>> {
    /**
     * Bridge method to LightStorage#hasChunk(long).
     */
    boolean bridge$hasChunk(long pos);

    /**
     * Bridge method to LightStorage#getDataForChunk(M, long).
     */
    ChunkNibbleArray bridge$getDataForChunk(M data, long chunk);

    /**
     * Bridge method to LightStorage#getStorageUncached().
     */
    M bridge$getStorageUncached();
}
