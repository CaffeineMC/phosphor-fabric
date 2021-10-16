package net.caffeinemc.phosphor.mixin.block;

import net.caffeinemc.phosphor.common.block.BlockStateLightInfo;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractBlock.AbstractBlockState.ShapeCache.class)
public class MixinShapeCache implements BlockStateLightInfo {
    @Shadow
    @Final
    VoxelShape[] extrudedFaces;

    @Shadow
    @Final
    int lightSubtracted;

    @Override
    public VoxelShape[] getExtrudedFaces() {
        return this.extrudedFaces;
    }

    @Override
    public int getLightSubtracted() {
        return this.lightSubtracted;
    }

}
