package me.jellysquid.mods.phosphor.mixin.block;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedBlockState;
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
    private static final Direction[] DIRECTIONS = Direction.values();

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

    private boolean hasSpecialLightShape;

    @Inject(method = "initShapeCache", at = @At(value = "RETURN"))
    private void onConstructed(CallbackInfo ci) {
        BlockState state = (BlockState) (Object) this;
        Block block = this.getBlock();

        if (!block.hasDynamicBounds()) {
            if (this.isOpaque()) {
                VoxelShape shape = block.method_9571(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN);

                this.lightShapes = new VoxelShape[DIRECTIONS.length];

                for (Direction dir : DIRECTIONS) {
                    this.lightShapes[dir.ordinal()] = VoxelShapes.method_16344(shape, dir);
                }
            }

            this.lightSubtracted = block.getLightSubtracted(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        } else {
            this.lightSubtracted = Integer.MIN_VALUE;
        }

        this.hasSpecialLightShape = this.isOpaque() && this.hasSidedTransparency();
    }

    @Override
    public boolean hasDynamicLightShape() {
        return this.lightShapes == null;
    }

    @Override
    public boolean hasSpecialLightShape() {
        return this.hasSpecialLightShape;
    }

    @Override
    public VoxelShape getStaticLightShape(Direction dir) {
        return this.lightShapes[dir.ordinal()];
    }

    @Override
    public VoxelShape getDynamicLightShape(BlockView view, BlockPos pos, Direction dir) {
        return VoxelShapes.method_16344(this.method_11615(view, pos), dir);
    }

    @Override
    public boolean hasDynamicLightOpacity() {
        return this.lightSubtracted == Integer.MIN_VALUE;
    }

    @Override
    public int getDynamicLightOpacity(BlockView view, BlockPos pos) {
        return this.getBlock().getLightSubtracted((BlockState) (Object) this, view, pos);
    }

    @Override
    public int getStaticLightOpacity() {
        return this.lightSubtracted;
    }
}
