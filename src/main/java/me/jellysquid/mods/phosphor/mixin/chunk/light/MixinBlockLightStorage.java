package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.jellysquid.mods.phosphor.common.chunk.light.BlockLightStorageAccess;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.BlockLightStorage;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockLightStorage.class)
public abstract class MixinBlockLightStorage extends LightStorage<BlockLightStorage.Data> implements BlockLightStorageAccess {
    private MixinBlockLightStorage(final LightType lightType, final ChunkProvider chunkProvider, final BlockLightStorage.Data lightData) {
        super(lightType, chunkProvider, lightData);
    }

    @Unique
    private final LongSet lightEnabled = new LongOpenHashSet();

    @Override
    protected void setLightEnabled(final long chunkPos, final boolean enable) {
        if (enable) {
            this.lightEnabled.add(chunkPos);
        } else {
            this.lightEnabled.remove(chunkPos);
        }
    }

    @Override
    public boolean isLightEnabled(final long sectionPos) {
        return this.lightEnabled.contains(ChunkSectionPos.withZeroZ(sectionPos));
    }
}
