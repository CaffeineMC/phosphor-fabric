package me.jellysquid.mods.phosphor.mixin.block;

import me.jellysquid.mods.phosphor.common.block.AbstractBlockStateAccess;
import me.jellysquid.mods.phosphor.common.block.ShapeCacheAccess;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class MixinAbstractBlockState implements AbstractBlockStateAccess {
    @Shadow
    protected AbstractBlock.AbstractBlockState.ShapeCache shapeCache;

    @SuppressWarnings("ConstantConditions")
    @Override
    public ShapeCacheAccess getShapeCache() {
        return (ShapeCacheAccess) (Object) this.shapeCache;
    }
}
