package me.jellysquid.mods.phosphor.common.mixins.lighting;

import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SPacketChunkData.class)
public abstract class MixinSPacketChunkData {
    @Inject(method = "calculateChunkSize", at = @At("HEAD"))
    private void onCalculateChunkSize(Chunk chunkIn, boolean p_189556_2_, int p_189556_3_, CallbackInfoReturnable<Integer> cir) {
        ((ILightingEngineProvider) chunkIn).getLightingEngine().processLightUpdates();
    }
}
