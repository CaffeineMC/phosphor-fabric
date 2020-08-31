package me.jellysquid.mods.phosphor.common.world;

import net.minecraft.util.math.ChunkPos;

public interface ThreadedAnvilChunkStorageAccess {
    void invokeReleaseLightTicket(ChunkPos pos);
}
