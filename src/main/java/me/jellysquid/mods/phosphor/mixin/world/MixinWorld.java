package me.jellysquid.mods.phosphor.mixin.world;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

@Mixin(World.class)
public abstract class MixinWorld implements WorldAccess {
    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/WorldChunk;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void addLightmap(final BlockPos pos, final BlockState state, final int flags, final int maxUpdateDepth, final CallbackInfoReturnable<Boolean> ci, final WorldChunk chunk) {
        if (ChunkSection.isEmpty(chunk.getSectionArray()[pos.getY() >> 4])) {
            this.getChunkManager().getLightingProvider().updateSectionStatus(pos, false);
        }
    }

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/chunk/WorldChunk;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;"
            )
        ),
        at = @At("RETURN"),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void removeLightmap(final BlockPos pos, final BlockState state, final int flags, final int maxUpdateDepth, final CallbackInfoReturnable<Boolean> ci, final WorldChunk chunk) {
        if (ChunkSection.isEmpty(chunk.getSectionArray()[pos.getY() >> 4])) {
            this.getChunkManager().getLightingProvider().updateSectionStatus(pos, true);
        }
    }
}
