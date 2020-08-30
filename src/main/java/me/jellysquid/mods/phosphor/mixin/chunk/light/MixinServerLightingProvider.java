package me.jellysquid.mods.phosphor.mixin.chunk.light;

import java.util.function.IntSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.LightingProvider;

@Mixin(ServerLightingProvider.class)
public abstract class MixinServerLightingProvider extends LightingProvider
{
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
    public void updateSectionStatus(final ChunkSectionPos pos, final boolean empty)
    {
        if (empty) {
            // Schedule after light updates have been carried out
            this.enqueue(pos.getSectionX(), pos.getSectionZ(), ServerLightingProvider.Stage.POST_UPDATE, Util.debugRunnable(() -> {
                super.updateSectionStatus(pos, true);
            },
                () -> "updateSectionStatus " + pos + " " + true
            ));
        } else {
            // Schedule before light updates are carried out
            this.enqueue(pos.getSectionX(), pos.getSectionZ(), () -> 0, ServerLightingProvider.Stage.PRE_UPDATE, Util.debugRunnable(() -> {
                super.updateSectionStatus(pos, false);
            },
                () -> "updateSectionStatus " + pos + " " + false
            ));

            // Schedule another version in POST_UPDATE to achieve reliable final state
            this.enqueue(pos.getSectionX(), pos.getSectionZ(), ServerLightingProvider.Stage.POST_UPDATE, Util.debugRunnable(() -> {
                super.updateSectionStatus(pos, false);
            },
                () -> "updateSectionStatus " + pos + " " + false
            ));
        }
    }
}
