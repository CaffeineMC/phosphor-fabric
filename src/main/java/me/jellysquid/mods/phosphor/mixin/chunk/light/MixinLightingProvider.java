package me.jellysquid.mods.phosphor.mixin.chunk.light;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import me.jellysquid.mods.phosphor.common.chunk.light.InitialLightingAccess;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightingProvider;

@Mixin(LightingProvider.class)
public abstract class MixinLightingProvider implements InitialLightingAccess
{
    @Shadow
    @Final
    private ChunkLightProvider<?, ?> blockLightProvider;

    @Shadow
    @Final
    private ChunkLightProvider<?, ?> skyLightProvider;

    @Override
    public void prepareInitialLighting(final long chunkPos)
    {
        if (this.blockLightProvider != null) {
            ((InitialLightingAccess) this.blockLightProvider).prepareInitialLighting(chunkPos);
        }

        if (this.skyLightProvider != null) {
            ((InitialLightingAccess) this.skyLightProvider).prepareInitialLighting(chunkPos);
        }
    }

    @Override
    public void cancelInitialLighting(final long chunkPos)
    {
        if (this.blockLightProvider != null) {
            ((InitialLightingAccess) this.blockLightProvider).cancelInitialLighting(chunkPos);
        }

        if (this.skyLightProvider != null) {
            ((InitialLightingAccess) this.skyLightProvider).cancelInitialLighting(chunkPos);
        }
    }
}

