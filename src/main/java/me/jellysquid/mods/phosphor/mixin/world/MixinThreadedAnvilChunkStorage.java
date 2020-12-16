package me.jellysquid.mods.phosphor.mixin.world;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.datafixers.util.Either;

import me.jellysquid.mods.phosphor.common.world.ThreadedAnvilChunkStorageAccess;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkHolder.Unloaded;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinThreadedAnvilChunkStorage implements ThreadedAnvilChunkStorageAccess {
    @Shadow
    protected abstract CompletableFuture<Either<List<Chunk>, Unloaded>> getRegion(final ChunkPos centerChunk, final int margin, final IntFunction<ChunkStatus> distanceToStatus);

    @Override
    @Invoker("releaseLightTicket")
    public abstract void invokeReleaseLightTicket(ChunkPos pos);

    @Shadow
    @Final
    private ThreadExecutor<Runnable> mainThreadExecutor;

    @Redirect(
        method = "makeChunkAccessible(Lnet/minecraft/server/world/ChunkHolder;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ChunkHolder;getChunkAt(Lnet/minecraft/world/chunk/ChunkStatus;Lnet/minecraft/server/world/ThreadedAnvilChunkStorage;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<Either<Chunk, Unloaded>> enforceNeighborsLoaded(final ChunkHolder holder, final ChunkStatus targetStatus, final ThreadedAnvilChunkStorage chunkStorage) {
        return holder.getChunkAt(ChunkStatus.FULL, (ThreadedAnvilChunkStorage) (Object) this).thenComposeAsync(
            either -> either.map(
                chunk -> this.getRegion(holder.getPos(), 1, ChunkStatus::byDistanceFromFull).thenApply(
                    either_ -> either_.mapLeft(list -> list.get(list.size() / 2))
                ),
                unloaded -> CompletableFuture.completedFuture(Either.right(unloaded))
            ),
            this.mainThreadExecutor
        );
    }
}
