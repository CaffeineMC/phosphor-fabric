package me.jellysquid.mods.phosphor.mixin.block;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedBlockState;
import me.jellysquid.mods.phosphor.common.util.LightUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockState.class)
public abstract class MixinBlockState implements ExtendedBlockState {
    @Shadow
    public abstract VoxelShape method_11615(BlockView view, BlockPos pos);

    @Shadow
    public abstract boolean isOpaque();

    @Shadow
    public abstract Block getBlock();

    @Shadow
    public abstract boolean hasSidedTransparency();

    private VoxelShape[] lightShapes;

    private int lightSubtracted;

    @Inject(method = "initShapeCache", at = @At(value = "RETURN"))
    private void onConstructed(CallbackInfo ci) {
        BlockState state = (BlockState) (Object) this;
        Block block = this.getBlock();

        this.lightShapes = LightUtil.NULL_LIGHT_SHAPES;
        this.lightSubtracted = Integer.MAX_VALUE;

        if (!block.hasDynamicBounds()) {
            if (this.isOpaque() && this.hasSidedTransparency()) {
                VoxelShape shape = block.method_9571(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN);

                this.lightShapes = new VoxelShape[LightUtil.DIRECTIONS.length];

                for (Direction dir : LightUtil.DIRECTIONS) {
                    this.lightShapes[dir.ordinal()] = VoxelShapes.method_16344(shape, dir);
                }
            } else {
                this.lightShapes = LightUtil.DEFAULT_LIGHT_SHAPES;
            }

            this.lightSubtracted = block.getLightSubtracted(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        }
    }

    @Override
    public VoxelShape getCachedLightShape(Direction dir) {
        return this.lightShapes[dir.ordinal()];
    }

    @Override
    public VoxelShape getLightShape(BlockView view, BlockPos pos, Direction dir) {
        return VoxelShapes.method_16344(this.method_11615(view, pos), dir);
    }

    @Override
    public boolean hasCachedLightOpacity() {
        return this.lightSubtracted != Integer.MAX_VALUE;
    }

    @Override
    public int getLightOpacity(BlockView view, BlockPos pos) {
        return this.getBlock().getLightSubtracted((BlockState) (Object) this, view, pos);
    }

    @Override
    public int getCachedLightOpacity() {
        return this.lightSubtracted;
    }
}
