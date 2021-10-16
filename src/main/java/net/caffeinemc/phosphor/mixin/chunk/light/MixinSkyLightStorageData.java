package net.caffeinemc.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.caffeinemc.phosphor.common.chunk.light.SharedSkyLightData;
import net.caffeinemc.phosphor.common.chunk.light.SkyLightStorageDataAccess;
import net.caffeinemc.phosphor.common.util.collections.DoubleBufferedLong2IntHashMap;
import net.caffeinemc.phosphor.common.util.collections.DoubleBufferedLong2ObjectHashMap;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SkyLightStorage.Data.class)
public abstract class MixinSkyLightStorageData extends MixinChunkToNibbleArrayMap
        implements SkyLightStorageDataAccess, SharedSkyLightData {
    @Shadow
    int minSectionY;

    @Mutable
    @Shadow
    @Final
    Long2IntOpenHashMap columnToTopSection;

    // Our new double-buffered collection
    @Unique
    private DoubleBufferedLong2IntHashMap topArraySectionYQueue;

    @Override
    public void makeSharedCopy(final DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> queue, final DoubleBufferedLong2IntHashMap topSectionQueue) {
        this.makeSharedCopy(queue);

        this.topArraySectionYQueue = topSectionQueue;

        // We need to immediately see all updates on the thread this is being copied to
        this.topArraySectionYQueue.flushChangesSync();
    }

    /**
     * @reason Avoid copying large data structures
     * @author JellySquid
     */
    @SuppressWarnings("ConstantConditions")
    @Overwrite
    public SkyLightStorage.Data copy() {
        // This will be called immediately by LightStorage in the constructor
        // We can take advantage of this fact to initialize our extra properties here without additional hacks
        if (!this.isInitialized()) {
            this.init();
        }

        SkyLightStorage.Data data = new SkyLightStorage.Data(this.arrays, this.columnToTopSection, this.minSectionY);
        ((SharedSkyLightData) (Object) data).makeSharedCopy(this.getUpdateQueue(), this.topArraySectionYQueue);

        return data;
    }

    @Override
    protected void init() {
        super.init();

        this.topArraySectionYQueue = new DoubleBufferedLong2IntHashMap();
        this.columnToTopSection = this.topArraySectionYQueue.createSyncView();
    }

    @Override
    public int getDefaultHeight() {
        return this.minSectionY;
    }

    @Override
    public int getHeight(long pos) {
        if (this.isShared()) {
            return this.topArraySectionYQueue.getAsync(pos);
        } else {
            return this.topArraySectionYQueue.getSync(pos);
        }
    }

    @Override
    public void updateMinHeight(final int y) {
        this.checkExclusiveOwner();

        if (this.minSectionY > y) {
            this.minSectionY = y;
            this.topArraySectionYQueue.defaultReturnValueSync(this.minSectionY);
        }
    }

    @Override
    public void setHeight(final long chunkPos, final int y) {
        this.checkExclusiveOwner();

        if (y > this.minSectionY) {
            this.topArraySectionYQueue.putSync(chunkPos, y);
        } else {
            this.topArraySectionYQueue.removeSync(chunkPos);
        }
    }
}
