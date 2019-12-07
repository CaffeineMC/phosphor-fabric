package me.jellysquid.mods.phosphor.common.chunk;

public interface ExtendedGenericLightStorage {
    /**
     * Bridge method to LightStorage#hasChunk(long).
     */
    boolean bridge$hasChunk(long pos);
}
