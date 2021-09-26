package net.caffeinemc.phosphor.common.chunk.light;

import java.util.concurrent.CompletableFuture;

import net.minecraft.world.chunk.Chunk;

public interface ServerLightingProviderAccess {
    CompletableFuture<Chunk> setupLightmaps(Chunk chunk);
}
