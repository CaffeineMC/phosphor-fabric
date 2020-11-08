package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.InitialLightingAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LightingProvider.class)
public abstract class MixinLightingProvider implements InitialLightingAccess
{
    @Shadow
    @Final
    private ChunkLightProvider<?, ?> blockLightProvider;

    @Shadow
    @Final
    private ChunkLightProvider<?, ?> skyLightProvider;

    @Shadow
    public void setSectionStatus(ChunkSectionPos pos, boolean notReady) {
    }

    @Shadow
    public void setColumnEnabled(ChunkPos pos, boolean lightEnabled) {
    }

    @Shadow
    public void setRetainData(ChunkPos pos, boolean retainData) {
    }

    @Shadow
    public void addLightSource(BlockPos pos, int level) {
    }

    @Override
    public void enableSourceLight(final long chunkPos) {
        if (this.blockLightProvider != null) {
            ((InitialLightingAccess) this.blockLightProvider).enableSourceLight(chunkPos);
        }

        if (this.skyLightProvider != null) {
            ((InitialLightingAccess) this.skyLightProvider).enableSourceLight(chunkPos);
        }
    }

    @Override
    public void enableLightUpdates(final long chunkPos) {
        if (this.blockLightProvider != null) {
            ((InitialLightingAccess) this.blockLightProvider).enableLightUpdates(chunkPos);
        }

        if (this.skyLightProvider != null) {
            ((InitialLightingAccess) this.skyLightProvider).enableLightUpdates(chunkPos);
        }
    }
}

