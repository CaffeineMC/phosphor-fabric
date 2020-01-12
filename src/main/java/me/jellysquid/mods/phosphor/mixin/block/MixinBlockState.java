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
    public abstract VoxelShape getCullingShape(BlockView view, BlockPos pos);

    @Shadow
    public abstract Block getBlock();

    private VoxelShape[] extrudedFaces;

    private int lightSubtracted;

    @Inject(method = "initShapeCache", at = @At(value = "RETURN"))
    private void init(CallbackInfo ci) {
        BlockState state = (BlockState) (Object) this;
        Block block = this.getBlock();

        if (block.hasDynamicBounds()) {
            this.extrudedFaces = state.isOpaque() ? LightUtil.NULL_LIGHT_SHAPES : LightUtil.EMPTY_LIGHT_SHAPES;
            this.lightSubtracted = Integer.MAX_VALUE;
        } else {
            if (state.isOpaque() && state.hasSidedTransparency()) {
                VoxelShape shape = block.getCullingShape(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN);

                this.extrudedFaces = new VoxelShape[LightUtil.DIRECTIONS.length];

                for (Direction dir : LightUtil.DIRECTIONS) {
                    this.extrudedFaces[dir.ordinal()] = VoxelShapes.extrudeFace(shape, dir);
                }
            } else {
                this.extrudedFaces = LightUtil.EMPTY_LIGHT_SHAPES;
            }

            this.lightSubtracted = block.getOpacity(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        }
    }

    @Override
    public VoxelShape getCachedExtrudedFace(Direction dir) {
        return this.extrudedFaces[dir.ordinal()];
    }

    @Override
    public VoxelShape getDynamicExtrudedFace(BlockView view, BlockPos pos, Direction dir) {
        return VoxelShapes.extrudeFace(this.getCullingShape(view, pos), dir);
    }

    @Override
    public boolean hasCachedLightOpacity() {
        return this.lightSubtracted != Integer.MAX_VALUE;
    }

    @Override
    public int getDynamicLightOpacity(BlockView view, BlockPos pos) {
        return this.getBlock().getOpacity((BlockState) (Object) this, view, pos);
    }

    @Override
    public int getCachedLightOpacity() {
        return this.lightSubtracted;
    }
}
