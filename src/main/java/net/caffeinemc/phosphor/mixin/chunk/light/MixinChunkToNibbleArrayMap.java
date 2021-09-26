package net.caffeinemc.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.caffeinemc.phosphor.common.util.collections.DoubleBufferedLong2ObjectHashMap;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkToNibbleArrayMap.class)
public abstract class MixinChunkToNibbleArrayMap {
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

    @Shadow
    @Final
    protected Long2ObjectOpenHashMap<ChunkNibbleArray> arrays;

    @Unique
    private DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> queue;
    @Unique
    private boolean isShared;
    // Indicates whether or not the extended data structures have been initialized
    @Unique
    private boolean init;

    @Unique
    protected boolean isShared() {
        return this.isShared;
    }

    @Unique
    protected boolean isInitialized() {
        return this.init;
    }

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
    @SuppressWarnings("OverwriteModifiers")
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

    @Unique
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
    @SuppressWarnings("OverwriteModifiers")
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
    @Unique
    protected void checkExclusiveOwner() {
        if (this.isShared) {
            throw new IllegalStateException("Tried to synchronously write to light data array table after it was made shareable");
        }
    }

    /**
     * Returns the queue of pending changes for this map.
     */
    @Unique
    protected DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> getUpdateQueue() {
        return this.queue;
    }

    /**
     * Makes this map a shared copy of another. The shared copy cannot be directly written into.
     */
    protected void makeSharedCopy(final DoubleBufferedLong2ObjectHashMap<ChunkNibbleArray> queue) {
        this.queue = queue;
        this.isShared = true;

        this.queue.flushChangesSync();

        // Copies of this map should not re-initialize the data structures!
        this.init = true;
    }

    /**
     * Initializes the data for this extended chunk array map. This should only be called once with the initialization
     * of a subtype.
     * @throws IllegalStateException If the map has already been initialized
     */
    @Unique
    protected void init() {
        if (this.init) {
            throw new IllegalStateException("Map already initialized");
        }

        this.queue = new DoubleBufferedLong2ObjectHashMap<>();
        this.init = true;
    }
}
