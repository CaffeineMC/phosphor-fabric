package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.phosphor.common.chunk.light.SharedBlockLightData;
import me.jellysquid.mods.phosphor.common.chunk.light.SharedNibbleArrayMap;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.BlockLightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockLightStorage.Data.class)
public abstract class MixinBlockLightStorageData extends ChunkToNibbleArrayMap<BlockLightStorage.Data>
        implements SharedBlockLightData {
    private boolean init;

    protected MixinBlockLightStorageData(Long2ObjectOpenHashMap<ChunkNibbleArray> arrays) {
        super(arrays);
    }

    @Override
    public void makeSharedCopy() {
        // Copies of this map should not re-initialize the data structures!
        this.init = true;
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
        if (!this.init) {
            this.initialize();
        }

        BlockLightStorage.Data data = new BlockLightStorage.Data(this.arrays);
        ((SharedNibbleArrayMap) (Object) data).makeSharedCopy((SharedNibbleArrayMap) this);
        ((SharedBlockLightData) (Object) data).makeSharedCopy();

        return data;
    }

    private void initialize() {
        ((SharedNibbleArrayMap) this).init();

        this.init = true;
    }
}
