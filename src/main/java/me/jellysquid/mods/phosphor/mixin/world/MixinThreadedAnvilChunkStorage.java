package me.jellysquid.mods.phosphor.mixin.world;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.datafixers.util.Either;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkHolder.Unloaded;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinThreadedAnvilChunkStorage
{
    @Shadow
    protected abstract CompletableFuture<Either<List<Chunk>, Unloaded>> createChunkRegionFuture(final ChunkPos centerChunk, final int margin, final IntFunction<ChunkStatus> distanceToStatus);

    @Redirect(
        method = "createBorderFuture(Lnet/minecraft/server/world/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ChunkHolder;createFuture(Lnet/minecraft/world/chunk/ChunkStatus;Lnet/minecraft/server/world/ThreadedAnvilChunkStorage;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<Either<Chunk, Unloaded>> enforceNeighborsLoaded(final ChunkHolder holder, final ChunkStatus targetStatus, final ThreadedAnvilChunkStorage chunkStorage) {
        return this.createChunkRegionFuture(holder.getPos(), 1, ChunkStatus::getTargetGenerationStatus).thenApply(either -> either.mapLeft(list -> list.get(list.size() / 2)));
    }
}
