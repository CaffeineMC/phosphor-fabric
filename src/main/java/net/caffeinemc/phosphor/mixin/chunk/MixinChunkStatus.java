package net.caffeinemc.phosphor.mixin.chunk;

import com.mojang.datafixers.util.Either;
import net.caffeinemc.phosphor.common.chunk.light.ServerLightingProviderAccess;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatus.class)
public class MixinChunkStatus {
    @Shadow
    private static ChunkStatus register(String id, ChunkStatus previous, int taskMargin, EnumSet<Heightmap.Type> heightMapTypes, ChunkStatus.ChunkType chunkType, ChunkStatus.GenerationTask task, ChunkStatus.LoadTask noGenTask) {
        return null;
    }

    @Shadow
    @Final
    private static ChunkStatus.LoadTask STATUS_BUMP_LOAD_TASK;

    @Redirect(
        method = "<clinit>",
        slice = @Slice(
            from = @At(value = "CONSTANT", args = "stringValue=features")
        ),
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/ChunkStatus;register(Ljava/lang/String;Lnet/minecraft/world/chunk/ChunkStatus;ILjava/util/EnumSet;Lnet/minecraft/world/chunk/ChunkStatus$ChunkType;Lnet/minecraft/world/chunk/ChunkStatus$GenerationTask;Lnet/minecraft/world/chunk/ChunkStatus$LoadTask;)Lnet/minecraft/world/chunk/ChunkStatus;",
            ordinal = 0
        )
    )
    private static ChunkStatus injectLightmapSetup(final String id, final ChunkStatus previous, final int taskMargin, final EnumSet<Heightmap.Type> heightMapTypes, final ChunkStatus.ChunkType chunkType, final ChunkStatus.GenerationTask task, final ChunkStatus.LoadTask loadTask) {
        return register(id, previous, taskMargin, heightMapTypes, chunkType,
            (status, executor, world, generator, structureManager, lightingProvider, function, surroundingChunks, chunk, force) ->
                task.doWork(status, executor, world, generator, structureManager, lightingProvider, function, surroundingChunks, chunk, force).thenCompose(
                    either -> getPreLightFuture(lightingProvider, either)
                ),
            (status, world, structureManager, lightingProvider, function, chunk) ->
                loadTask.doWork(status, world, structureManager, lightingProvider, function, chunk).thenCompose(
                    either -> getPreLightFuture(lightingProvider, either)
                )
            );
    }

    @Unique
    private static CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> getPreLightFuture(final ServerLightingProvider lightingProvider, final Either<Chunk, ChunkHolder.Unloaded> either) {
        return either.map(
            chunk -> getPreLightFuture(lightingProvider, chunk),
            unloaded -> CompletableFuture.completedFuture(Either.right(unloaded))
        );
    }

    @Unique
    private static CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> getPreLightFuture(final ServerLightingProvider lightingProvider, final Chunk chunk) {
        return ((ServerLightingProviderAccess) lightingProvider).setupLightmaps(chunk).thenApply(Either::left);
    }
}
