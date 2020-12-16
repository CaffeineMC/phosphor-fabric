package me.jellysquid.mods.phosphor.common.util.collections;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.concurrent.locks.StampedLock;

/**
 * A double buffered Long->Object hash table which allows for multiple readers to see a consistent view without\
 * contention over shared resources. The synchronous (owned) view must be synced using
 * {@link DoubleBufferedLong2IntHashMap#flushChangesSync()} after all desired changes have been made.
 *
 * Methods labeled as synchronous access the owned mutable view of this map which behaves as the back-buffer. When
 * changes are flushed, the front-buffer is swapped with the back-buffer, and any pending changes are written into the
 * new back-buffer. The locking implementation is optimized for (relatively) infrequent flips.
 *
 * Null is used to indicate a value to be removed, and as such, cannot be used as a value type in the collection. If
 * you need to remove an element, use {@link DoubleBufferedLong2IntHashMap#removeSync(long)}.
 */
public class DoubleBufferedLong2ObjectHashMap<V> {
    // The map of pending entry updates to be applied to the visible hash table
    private final Long2ObjectMap<V> mapPending;

    // The hash table of entries belonging to the owning thread
    private Long2ObjectMap<V> mapLocal;

    // The hash table of entries available to other threads
    private Long2ObjectMap<V> mapShared;

    // The lock used by other threads to grab values from the visible map asynchronously. This prevents other threads
    // from seeing partial updates while the changes are flushed. The lock implementation is specially selected to
    // optimize for the common case: infrequent writes, very frequent reads.
    private final StampedLock lock = new StampedLock();

    public DoubleBufferedLong2ObjectHashMap() {
        this(16, Hash.FAST_LOAD_FACTOR);
    }

    public DoubleBufferedLong2ObjectHashMap(final int capacity, final float loadFactor) {
        this.mapLocal = new Long2ObjectOpenHashMap<>(capacity, loadFactor);
        this.mapShared = new Long2ObjectOpenHashMap<>(capacity, loadFactor);
        this.mapPending = new Long2ObjectOpenHashMap<>(capacity, loadFactor);
    }

    public V getSync(long k) {
        return this.mapLocal.get(k);
    }

    public V putSync(long k, V value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null, use enqueueRemoveSync instead to remove entries");
        }

        this.mapPending.put(k, value);

        return this.mapLocal.put(k, value);
    }

    public V removeSync(long k) {
        this.mapPending.put(k, null);

        return this.mapLocal.remove(k);
    }

    public boolean containsSync(long k) {
        return this.mapLocal.containsKey(k);
    }

    public V getAsync(long k) {
        long stamp;
        V ret = null;

        do {
            stamp = this.lock.tryOptimisticRead();

            try {
                ret = this.mapShared.get(k);
            } catch (ArrayIndexOutOfBoundsException ignored) { } // Swallow memory errors on failed optimistic reads
        } while (!this.lock.validate(stamp));

        return ret;
    }

    public void flushChangesSync() {
        // Early-exit if there's no work to do
        if (this.mapPending.isEmpty()) {
            return;
        }

        // Swap the local and shared tables immediately, and then block the writer thread while we finish copying
        this.swapTables();

        // Use a non-allocating iterator if possible, otherwise we're going to hurt
        for (Long2ObjectMap.Entry<V> entry : Long2ObjectMaps.fastIterable(this.mapPending)) {
            final long key = entry.getLongKey();
            final V val = entry.getValue();

            if (val == null) {
                this.mapLocal.remove(key);
            } else {
                this.mapLocal.put(key, val);
            }
        }

        this.mapPending.clear();
    }

    private void swapTables() {
        final long writeLock =  this.lock.writeLock();

        Long2ObjectMap<V> mapShared = this.mapLocal;
        Long2ObjectMap<V> mapLocal = this.mapShared;

        this.mapShared = mapShared;
        this.mapLocal = mapLocal;

        this.lock.unlockWrite(writeLock);
    }
}