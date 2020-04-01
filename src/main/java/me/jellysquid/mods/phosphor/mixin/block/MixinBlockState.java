package me.jellysquid.mods.phosphor.mixin.block;

import me.jellysquid.mods.phosphor.common.block.BlockStateAccess;
import me.jellysquid.mods.phosphor.common.block.ShapeCacheAccess;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockState.class)
public abstract class MixinBlockState implements BlockStateAccess {
    @Shadow
    private BlockState.ShapeCache shapeCache;

    @Override
    public ShapeCacheAccess getShapeCache() {
        return (ShapeCacheAccess) this.shapeCache;
    }
}
