package me.jellysquid.mods.phosphor.mixin.client.world;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.light.LightingProvider;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld
{
    @Redirect(
        method = "unloadBlockEntities(Lnet/minecraft/world/chunk/WorldChunk;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/light/LightingProvider;setColumnEnabled(Lnet/minecraft/util/math/ChunkPos;Z)V"
        )
    )
    private void cancelDisableLightUpdates(final LightingProvider lightingProvider, final ChunkPos pos, final boolean enable) {
    }
}
