package me.jellysquid.mods.phosphor.common.chunk.light;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;

public interface LightStorageAccess<M extends ChunkToNibbleArrayMap<M>> {
    /**
     * Bridge method to LightStorage#getStorageUncached().
     */
    M getStorageUncached();
}
