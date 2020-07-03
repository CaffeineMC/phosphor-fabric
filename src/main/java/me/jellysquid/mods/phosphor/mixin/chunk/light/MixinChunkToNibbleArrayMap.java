package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.SharedNibbleArrayMap;
import me.jellysquid.mods.phosphor.common.util.collections.DoubleBufferedLong2ObjectHashMap;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import org.spongepowered.asm.mixin.*;

@SuppressWarnings("OverwriteModifiers")
@Mixin(ChunkToNibbleArrayMap.class)
public abstract class MixinChunkToNibbleArrayMap implements SharedNibbleArrayMap {
    @Shadow
    private boolean cacheEnabled;

    @Shadow
    @Final
    private long[] cachePositions;

    @Shadow
    @Final
    private ChunkNibbleArray[] cacheArrays;

    @Shadow
    public abstract void clearCache();

    private DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> queue;
    private boolean isShared;

    /**
     * @reason Allow shared access, avoid copying
     * @author JellySquid
     */
    @Overwrite
    public void replaceWithCopy(long pos) {
        this.checkExclusiveOwner();

        this.queue.putSync(pos, this.queue.getSync(pos).copy());

        this.clearCache();
    }

    /**
     * @reason Allow shared access, avoid copying
     * @author JellySquid
     */
    @Overwrite
    public ChunkNibbleArray get(long pos) {
        if (this.cacheEnabled) {
            // Hoist array field access out of the loop to allow the JVM to drop bounds checks
            long[] cachePositions = this.cachePositions;

            for(int i = 0; i < cachePositions.length; ++i) {
                if (pos == cachePositions[i]) {
                    return this.cacheArrays[i];
                }
            }
        }

        // Move to a separate method to help the JVM inline methods
        return this.getUncached(pos);
    }

    private ChunkNibbleArray getUncached(long pos) {
        ChunkNibbleArray array;

        if (this.isShared) {
            array = this.queue.getAsync(pos);
        } else {
            array = this.queue.getSync(pos);
        }

        if (array == null) {
            return null;
        }

        if (this.cacheEnabled) {
            long[] cachePositions = this.cachePositions;
            ChunkNibbleArray[] cacheArrays = this.cacheArrays;

            for(int i = cacheArrays.length - 1; i > 0; --i) {
                cachePositions[i] = cachePositions[i - 1];
                cacheArrays[i] = cacheArrays[i - 1];
            }

            cachePositions[0] = pos;
            cacheArrays[0] = array;
        }

        return array;
    }

    /**
     * @reason Allow shared access, avoid copying
     * @author JellySquid
     */
    @Overwrite
    public void put(long pos, ChunkNibbleArray data) {
        this.checkExclusiveOwner();

        this.queue.putSync(pos, data);
    }

    /**
     * @reason Allow shared access, avoid copying
     * @author JellySquid
     */
    @Overwrite
    public ChunkNibbleArray removeChunk(long chunkPos) {
        this.checkExclusiveOwner();

        return this.queue.removeSync(chunkPos);
    }

    /**
     * @reason Allow shared access, avoid copying
     * @author JellySquid
     */
    @Overwrite
    public boolean containsKey(long chunkPos) {
        if (this.isShared) {
            return this.queue.getAsync(chunkPos) != null;
        } else {
            return this.queue.containsSync(chunkPos);
        }
    }

    /**
     * Check if the light array table is exclusively owned (not shared). If not, an exception is thrown to catch the
     * invalid state. Synchronous writes can only occur while the table is exclusively owned by the writer/actor thread.
     */
    private void checkExclusiveOwner() {
        if (this.isShared) {
            throw new IllegalStateException("Tried to synchronously write to light data array table after it was made shareable");
        }
    }

    @Override
    public DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> getUpdateQueue() {
        return this.queue;
    }

    @Override
    public void makeSharedCopy(SharedNibbleArrayMap map) {
        this.queue = map.getUpdateQueue();
        this.isShared = this.queue != null;

        if (this.isShared) {
            this.queue.flushChangesSync();
        }
    }

    @Override
    public void init() {
        if (this.queue != null) {
            throw new IllegalStateException("Map already initialized");
        }

        this.queue = new DoubleBufferedLong2ObjectHashMap<>();
    }
}
