package me.jellysquid.mods.phosphor.mixin.block;

import me.jellysquid.mods.phosphor.common.block.BlockStateLightInfo;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractBlock.AbstractBlockState.ShapeCache.class)
public class MixinShapeCache implements BlockStateLightInfo {
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

}
