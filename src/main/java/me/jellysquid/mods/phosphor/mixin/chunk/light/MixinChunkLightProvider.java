package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedBlockState;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedChunkLightProvider;
import me.jellysquid.mods.phosphor.common.util.cache.LightEngineBlockAccess;
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
public class MixinChunkLightProvider<M extends WorldNibbleStorage<M>, S extends LightStorage<M>> implements ExtendedChunkLightProvider {
    @Shadow
    @Final
    protected BlockPos.Mutable field_19284;

    @Shadow
    @Final
    protected ChunkProvider chunkProvider;

    private LightEngineBlockAccess blockAccess;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(ChunkProvider provider, LightType lightType, S storage, CallbackInfo ci) {
        this.blockAccess = new LightEngineBlockAccess(provider);
    }

    @Inject(method = "method_17530", at = @At("RETURN"))
    private void onCleanup(CallbackInfo ci) {
        // This callback may be executed from the constructor above, and the object won't be initialized then
        if (this.blockAccess != null) {
            this.blockAccess.reset();
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public BlockState getBlockStateForLighting(int x, int y, int z) {
        return this.blockAccess.getBlockState(x, y, z);
    }

    // [VanillaCopy] method_20479
    @Override
    public int getSubtractedLight(BlockState state, int x, int y, int z) {
        ExtendedBlockState estate = ((ExtendedBlockState) state);

        if (estate.hasCachedLightOpacity()) {
            return estate.getCachedLightOpacity();
        }

        return estate.getLightOpacity(this.chunkProvider.getWorld(), this.field_19284.set(x, y, z));
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getVoxelShape(BlockState state, int x, int y, int z, Direction dir) {
        ExtendedBlockState estate = ((ExtendedBlockState) state);

        VoxelShape shape = estate.getCachedLightShape(dir);

        if (shape != null) {
            return shape;
        }

        return estate.getLightShape(this.chunkProvider.getWorld(), this.field_19284.set(x, y, z), dir);
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getVoxelShape(int x, int y, int z, Direction dir) {
        BlockState state = this.blockAccess.getBlockState(x, y, z);

        if (state == null) {
            return VoxelShapes.fullCube();
        }

        return this.getVoxelShape(state, x, y, z, dir);
    }
}
