package net.caffeinemc.phosphor.mixin.client.world;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.caffeinemc.phosphor.common.util.IProfiling;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.light.LightingProvider;

@Mixin(ClientChunkManager.class)
public abstract class MixinClientChunkManager {
    @Shadow
    @Final
    private LightingProvider lightingProvider;

    MixinClientChunkManager() {
    }

    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void setProfiler(final ClientWorld world, final int loadDistance, final CallbackInfo ci) {
        ((IProfiling) this.lightingProvider).setProfiler(world.getProfilerSupplier());
    }
}
