package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.jellysquid.mods.phosphor.common.chunk.light.SkyLightStorageDataAccess;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SkyLightStorage.Data.class)
public class MixinSkyLightStorageData implements SkyLightStorageDataAccess {
    @Shadow
    private int defaultTopArraySectionY;

    @Shadow
    @Final
    private Long2IntOpenHashMap topArraySectionY;

    @Override
    public int getDefaultHeight() {
        return this.defaultTopArraySectionY;
    }

    @Override
    public Long2IntOpenHashMap getHeightMap() {
        return this.topArraySectionY;
    }
}
