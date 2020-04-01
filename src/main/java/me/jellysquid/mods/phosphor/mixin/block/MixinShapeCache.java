package me.jellysquid.mods.phosphor.mixin.block;

import me.jellysquid.mods.phosphor.common.block.ShapeCacheAccess;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractBlock.AbstractBlockState.ShapeCache.class)
public class MixinShapeCache implements ShapeCacheAccess {
    @Shadow
    @Final
    private VoxelShape[] extrudedFaces;

    @Shadow
    @Final
    private int lightSubtracted;

    @Override
    public VoxelShape[] getExtrudedFaces() {
        return this.extrudedFaces;
    }

    @Override
    public int getLightSubtracted() {
        return this.lightSubtracted;
    }

    @Override
    public boolean isOpaque() {
        return this.extrudedFaces != null;
    }
}
