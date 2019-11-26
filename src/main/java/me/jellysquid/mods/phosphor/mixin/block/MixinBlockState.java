package me.jellysquid.mods.phosphor.mixin.block;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedBlockState;
import me.jellysquid.mods.phosphor.common.chunk.PhosphorBlockStateCache;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockState.class)
public abstract class MixinBlockState implements ExtendedBlockState {
    private boolean shouldFetchCullState;

    @Shadow
    public abstract VoxelShape method_11615(BlockView blockView_1, BlockPos blockPos_1);

    @Shadow
    public abstract boolean isOpaque();

    @Shadow
    public abstract boolean hasSidedTransparency();

    private PhosphorBlockStateCache phosphorBlockStateCache;

    @Inject(method = "initShapeCache", at = @At(value = "RETURN"))
    private void onConstructed(CallbackInfo ci) {
        this.phosphorBlockStateCache = new PhosphorBlockStateCache(((BlockState) (Object) this));
        this.shouldFetchCullState = this.isOpaque() && this.hasSidedTransparency();
    }

    @Override
    public boolean hasDynamicShape() {
        return this.phosphorBlockStateCache != null;
    }

    @Override
    public boolean hasSpecialLightingShape() {
        return this.shouldFetchCullState;
    }

    @Override
    public VoxelShape getStaticLightShape(Direction dir) {
        return this.phosphorBlockStateCache.shapes[dir.ordinal()];
    }

    @Override
    public VoxelShape getDynamicLightShape(BlockView view, BlockPos pos, Direction dir) {
        return VoxelShapes.method_16344(this.method_11615(view, pos), dir);
    }
}
