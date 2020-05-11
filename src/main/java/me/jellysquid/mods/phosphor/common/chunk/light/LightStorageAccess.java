package me.jellysquid.mods.phosphor.common.chunk.light;

import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;

import java.util.concurrent.locks.StampedLock;

public interface LightStorageAccess<M extends ChunkToNibbleArrayMap<M>> {
    /**
     * Bridge method to LightStorage#getStorageUncached().
     */
    M getUncachedStorage();

    /**
     * Returns the lock which wraps the {@link LightStorageAccess#getUncachedStorage()}. Locking should always be
     * performed when accessing values in the aforementioned storage.
     */
    StampedLock getUncachedStorageLock();
}
