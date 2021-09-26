package net.caffeinemc.phosphor.common.chunk.light;

public interface InitialLightingAccess {
    void enableSourceLight(long chunkPos);

    void enableLightUpdates(long chunkPos);
}
