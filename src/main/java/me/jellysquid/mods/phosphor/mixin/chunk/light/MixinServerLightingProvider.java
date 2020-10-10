package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.InitialLightingAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.ServerLightingProviderAccess;
import me.jellysquid.mods.phosphor.common.world.ThreadedAnvilChunkStorageAccess;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;

@Mixin(ServerLightingProvider.class)
public abstract class MixinServerLightingProvider extends LightingProvider implements ServerLightingProviderAccess {
    private MixinServerLightingProvider(final ChunkProvider chunkProvider, final boolean hasBlockLight, final boolean hasSkyLight) {
        super(chunkProvider, hasBlockLight, hasSkyLight);
    }

    @Shadow
    protected abstract void enqueue(int x, int z, IntSupplier completedLevelSupplier, ServerLightingProvider.Stage stage, Runnable task);

    @Shadow
    protected abstract void enqueue(int x, int z, ServerLightingProvider.Stage stage, Runnable task);

    /**
     * @author PhiPro
     * @reason Re-implement
     */
    @Overwrite
    public void setSectionStatus(final ChunkSectionPos pos, final boolean empty)
    {
        if (empty) {
            // Schedule after light updates have been carried out
            this.enqueue(pos.getSectionX(), pos.getSectionZ(), ServerLightingProvider.Stage.POST_UPDATE, Util.debugRunnable(() -> {
                super.setSectionStatus(pos, true);
            },
                () -> "updateSectionStatus " + pos + " " + true
            ));
        } else {
            // Schedule before light updates are carried out
            this.enqueue(pos.getSectionX(), pos.getSectionZ(), () -> 0, ServerLightingProvider.Stage.PRE_UPDATE, Util.debugRunnable(() -> {
                super.setSectionStatus(pos, false);
            },
                () -> "updateSectionStatus " + pos + " " + false
            ));

            // Schedule another version in POST_UPDATE to achieve reliable final state
            this.enqueue(pos.getSectionX(), pos.getSectionZ(), ServerLightingProvider.Stage.POST_UPDATE, Util.debugRunnable(() -> {
                super.setSectionStatus(pos, false);
            },
                () -> "updateSectionStatus " + pos + " " + false
            ));
        }
    }

    @Override
    public CompletableFuture<Chunk> setupLightmaps(final Chunk chunk) {
        final ChunkPos chunkPos = chunk.getPos();

        // This evaluates the non-empty subchunks concurrently on the lighting thread...
        this.enqueue(chunkPos.x, chunkPos.z, () -> 0, ServerLightingProvider.Stage.PRE_UPDATE, Util.debugRunnable(() -> {
            final ChunkSection[] chunkSections = chunk.getSectionArray();

            for (int i = 0; i < chunkSections.length; ++i) {
                if (!ChunkSection.isEmpty(chunkSections[i])) {
                    super.setSectionStatus(ChunkSectionPos.from(chunkPos, i), false);
                }
            }

            if (chunk.isLightOn()) {
                super.setColumnEnabled(chunkPos, true);
            } else {
                ((InitialLightingAccess) this).prepareInitialLighting(ChunkSectionPos.asLong(chunkPos.x, 0, chunkPos.z));
            }
        },
            () -> "setupLightmaps " + chunkPos
        ));

        return CompletableFuture.supplyAsync(() -> {
            super.setRetainData(chunkPos, false);
            return chunk;
        },
            (runnable) -> this.enqueue(chunkPos.x, chunkPos.z, () -> 0, ServerLightingProvider.Stage.POST_UPDATE, runnable)
        );
    }

    @Inject(
        method = "updateChunkStatus(Lnet/minecraft/util/math/ChunkPos;)V",
        at = @At("TAIL")
    )
    private void cancelInitialLighting(final ChunkPos pos, final CallbackInfo ci) {
        this.enqueue(pos.x, pos.z, () -> 0, ServerLightingProvider.Stage.PRE_UPDATE, Util.debugRunnable(() -> {
            ((InitialLightingAccess) this).cancelInitialLighting(ChunkSectionPos.asLong(pos.x, 0, pos.z));
        },
            () -> "cancelInitialLighting " + pos
        ));
    }

    @Shadow
    @Final
    private ThreadedAnvilChunkStorage chunkStorage;

    /**
     * @author PhiPro
     * @reason Move parts of the logic to {@link #setupLightmaps(Chunk)}
     */
    @Overwrite
    public CompletableFuture<Chunk> light(Chunk chunk, boolean excludeBlocks) {
        final ChunkPos chunkPos = chunk.getPos();

        this.enqueue(chunkPos.x, chunkPos.z, ServerLightingProvider.Stage.PRE_UPDATE, Util.debugRunnable(() -> {
            if (!chunk.isLightOn()) {
                super.setColumnEnabled(chunkPos, true);
            }

            if (!excludeBlocks) {
                chunk.getLightSourcesStream().forEach((blockPos) -> {
                    super.addLightSource(blockPos, chunk.getLuminance(blockPos));
                });
            }
        },
            () -> "lightChunk " + chunkPos + " " + excludeBlocks
        ));

        return CompletableFuture.supplyAsync(() -> {
            chunk.setLightOn(true);
            ((ThreadedAnvilChunkStorageAccess) this.chunkStorage).invokeReleaseLightTicket(chunkPos);

            return chunk;
        },
            (runnable) -> this.enqueue(chunkPos.x, chunkPos.z, ServerLightingProvider.Stage.POST_UPDATE, runnable)
        );
    }
}
