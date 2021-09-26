package net.caffeinemc.phosphor.common.world;

import net.minecraft.util.math.ChunkPos;

public interface ThreadedAnvilChunkStorageAccess {
    void invokeReleaseLightTicket(ChunkPos pos);
}
