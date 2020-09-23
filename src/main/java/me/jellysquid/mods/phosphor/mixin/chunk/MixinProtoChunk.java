package me.jellysquid.mods.phosphor.mixin.chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.light.LightingProvider;

@Mixin(ProtoChunk.class)
public abstract class MixinProtoChunk {
    @Shadow
    public abstract LightingProvider getLightingProvider();

    @Shadow
    public abstract ChunkStatus getStatus();

    @Unique
    private static final ChunkStatus PRE_LIGHT = ChunkStatus.LIGHT.getPrevious();

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;)Lnet/minecraft/block/BlockState;"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void addLightmap(final BlockPos pos, final BlockState state, final boolean moved, final CallbackInfoReturnable<BlockState> ci, final int x, final int y, final int z, final ChunkSection section) {
        if (this.getStatus().isAtLeast(PRE_LIGHT) && ChunkSection.isEmpty(section)) {
            this.getLightingProvider().setSectionStatus(pos, false);
        }
    }

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;)Lnet/minecraft/block/BlockState;"
            )
        ),
        at = @At(
            value = "RETURN",
            ordinal = 0
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void removeLightmap(final BlockPos pos, final BlockState state, final boolean moved, final CallbackInfoReturnable<BlockState> ci, final int x, final int y, final int z, final ChunkSection section) {
        if (this.getStatus().isAtLeast(PRE_LIGHT) && ChunkSection.isEmpty(section)) {
            this.getLightingProvider().setSectionStatus(pos, true);
        }
    }
}
