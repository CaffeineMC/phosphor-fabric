package me.jellysquid.mods.phosphor.common.mixins.lighting;

import me.jellysquid.mods.phosphor.api.IChunkLightingData;
import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import me.jellysquid.mods.phosphor.common.world.LightingHooks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader {
    @Inject(method = "saveChunk", at = @At("HEAD"))
    private void onConstructed(World world, Chunk chunkIn, CallbackInfo callbackInfo) {
        ((ILightingEngineProvider) world).getLightingEngine().processLightUpdates();
    }

    @Inject(method = "readChunkFromNBT", at = @At("RETURN"))
    private void onReadChunkFromNBT(World world, NBTTagCompound compound, CallbackInfoReturnable<Chunk> cir) {
        Chunk chunk = cir.getReturnValue();

        LightingHooks.readNeighborLightChecksFromNBT(chunk, compound);

        ((IChunkLightingData) chunk).setLightInitialized(compound.getBoolean("LightPopulated"));

    }

    @Inject(method = "writeChunkToNBT", at = @At("RETURN"))
    private void onWriteChunkToNBT(Chunk chunk, World world, NBTTagCompound compound, CallbackInfo ci) {
        LightingHooks.writeNeighborLightChecksToNBT(chunk, compound);

        compound.setBoolean("LightPopulated", ((IChunkLightingData) chunk).isLightInitialized());
    }
}
