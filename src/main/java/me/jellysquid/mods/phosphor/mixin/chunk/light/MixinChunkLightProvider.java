package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedBlockState;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedMixinChunkLightProvider;
import me.jellysquid.mods.phosphor.common.util.cache.CachedChunkSectionAccess;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.WorldNibbleStorage;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkLightProvider.class)
public class MixinChunkLightProvider<M extends WorldNibbleStorage<M>, S extends LightStorage<M>> implements ExtendedMixinChunkLightProvider {
    @Shadow
    @Final
    protected BlockPos.Mutable field_19284;

    @Shadow
    @Final
    protected ChunkProvider chunkProvider;

    private CachedChunkSectionAccess cacher;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(ChunkProvider provider, LightType lightType, S storage, CallbackInfo ci) {
        this.cacher = new CachedChunkSectionAccess(provider);
    }

    @Inject(method = "method_17530", at = @At("RETURN"))
    private void onCleanup(CallbackInfo ci) {
        // This callback may be executed from the constructor above, and the object won't be initialized then
        if (this.cacher != null) {
            this.cacher.cleanup();
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public BlockState getBlockStateForLighting(int x, int y, int z) {
        return this.cacher.getBlockState(x, y, z);
    }

    // [VanillaCopy] method_20479
    @Override
    public int getSubtractedLight(BlockState state, int x, int y, int z) {
        return state.getLightSubtracted(this.chunkProvider.getWorld(), this.field_19284.set(x, y, z));
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getVoxelShape(BlockState state, int x, int y, int z, Direction dir) {
        ExtendedBlockState estate = ((ExtendedBlockState) state);

        if (estate.hasSpecialLightingShape()) {
            if (estate.hasDynamicShape()) {
                return estate.getStaticLightShape(dir);
            } else {
                return estate.getDynamicLightShape(this.chunkProvider.getWorld(), this.field_19284.set(x, y, z), dir);
            }
        } else {
            return VoxelShapes.empty();
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getVoxelShape(int x, int y, int z, Direction dir) {
        BlockState state = this.cacher.getBlockState(x, y, z);

        if (state == null) {
            return VoxelShapes.fullCube();
        }

        return this.getVoxelShape(state, x, y, z, dir);
    }
}
