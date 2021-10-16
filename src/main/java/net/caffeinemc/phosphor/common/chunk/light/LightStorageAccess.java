package net.caffeinemc.phosphor.common.chunk.light;

import net.minecraft.world.chunk.light.ChunkLightProvider;

public interface LightStorageAccess {
    boolean callHasSection(long sectionPos);

    /**
     * Runs scheduled cleanups of light data
     */
    void runCleanups();

    void enableLightUpdates(long chunkPos);

    /**
     * Disables light updates and source light for the provided <code>chunkPos</code> and additionally removes all light data associated to the chunk.
     */
    void disableChunkLight(long chunkPos, ChunkLightProvider<?, ?> lightProvider);

    void invokeSetColumnEnabled(long chunkPos, boolean enabled);
}
