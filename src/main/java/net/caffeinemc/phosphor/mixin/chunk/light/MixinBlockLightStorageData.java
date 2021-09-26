package net.caffeinemc.phosphor.mixin.chunk.light;

import net.caffeinemc.phosphor.common.chunk.light.SharedBlockLightData;
import net.caffeinemc.phosphor.common.util.collections.DoubleBufferedLong2ObjectHashMap;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.BlockLightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockLightStorage.Data.class)
public abstract class MixinBlockLightStorageData extends MixinChunkToNibbleArrayMap implements SharedBlockLightData {
    @Override
    public void makeSharedCopy(final DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> queue) {
        super.makeSharedCopy(queue);
    }

    /**
     * @reason Use double-buffering to avoid copying
     * @author JellySquid
     */
    @SuppressWarnings("ConstantConditions")
    @Overwrite
    public BlockLightStorage.Data copy() {
        // This will be called immediately by LightStorage in the constructor
        // We can take advantage of this fact to initialize our extra properties here without additional hacks
        if (!this.isInitialized()) {
            this.init();
        }

        BlockLightStorage.Data data = new BlockLightStorage.Data(this.arrays);
        ((SharedBlockLightData) (Object) data).makeSharedCopy(this.getUpdateQueue());

        return data;
    }
}
