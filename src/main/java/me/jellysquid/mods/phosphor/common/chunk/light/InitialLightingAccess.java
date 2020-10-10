package me.jellysquid.mods.phosphor.common.chunk.light;

public interface InitialLightingAccess {
    void prepareInitialLighting(long chunkPos);

    void cancelInitialLighting(long chunkPos);
}
