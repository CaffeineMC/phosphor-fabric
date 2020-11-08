package me.jellysquid.mods.phosphor.common.chunk.light;

import net.minecraft.world.chunk.ChunkNibbleArray;

public interface LightStorageAccess {
    ChunkNibbleArray callGetLightSection(long sectionPos, boolean cached);

    /**
     * Returns the light value for a position that does not have an associated lightmap.
     * This is analogous to {@link net.minecraft.world.chunk.light.LightStorage#getLight(long)}, but uses the cached light data.
     */
    int getLightWithoutLightmap(long blockPos);

    /**
     * Enables or disables light updates for the provided <code>chunkPos</code>.
     * Disabling light updates additionally disables source light and removes all data associated to the chunk.
     */
    void setLightUpdatesEnabled(long chunkPos, boolean enabled);

    void invokeSetColumnEnabled(long chunkPos, boolean enabled);
}
