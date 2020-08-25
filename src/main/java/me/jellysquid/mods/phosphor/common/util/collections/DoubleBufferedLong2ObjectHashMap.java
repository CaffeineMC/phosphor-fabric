package me.jellysquid.mods.phosphor.common.util.collections;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
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
 * changes are flushed, the front-buffer is locked, written into, and then unlocked. The locking implementation is
 * optimized for (relatively) infrequent flips.
 *
 * Null is used to indicate a value to be removed, and as such, cannot be used as a value type in the collection. If
 * you need to remove an element, use {@link DoubleBufferedLong2IntHashMap#removeSync(long)}.
 */
public class DoubleBufferedLong2ObjectHashMap<V> {
    // The hash table of entries belonging to the owning thread
    private final Long2ObjectMap<V> mapPending;

    // The hash table of entries available to other threads
    private final Long2ObjectMap<V> mapLive;

    // The map of pending entry updates to be applied to the visible hash table
    private final Long2ObjectMap<V> mapUpdates;

    // The lock used by other threads to grab values from the visible map asynchronously. This prevents other threads
    // from seeing partial updates while the changes are flushed. The lock implementation is specially selected to
    // optimize for the common case: infrequent writes, very frequent reads.
    private final StampedLock lock = new StampedLock();

    public DoubleBufferedLong2ObjectHashMap() {
        this(16, Hash.FAST_LOAD_FACTOR);
    }

    public DoubleBufferedLong2ObjectHashMap(final int capacity, final float loadFactor) {
        this.mapPending = new Long2ObjectOpenHashMap<>(capacity, loadFactor);
        this.mapLive = new Long2ObjectOpenHashMap<>(capacity, loadFactor);
        this.mapUpdates = new Long2ObjectOpenHashMap<>(capacity, loadFactor);
    }

    public V getSync(long k) {
        return this.mapPending.get(k);
    }

    public V putSync(long k, V value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null, use enqueueRemoveSync instead to remove entries");
        }

        this.mapUpdates.put(k, value);

        return this.mapPending.put(k, value);
    }

    public V removeSync(long k) {
        this.mapUpdates.put(k, null);

        return this.mapPending.remove(k);
    }

    public boolean containsSync(long k) {
        return this.mapPending.containsKey(k);
    }

    public V getAsync(long k) {
        while (true) {
            final long stamp = this.lock.tryOptimisticRead();

            // Long2ObjectOpenHashMap is not thread-safe and may throw ArrayIndexOutOfBoundsException when queried in an inconsistent state
            try {
                final V ret = this.mapLive.get(k);

                if (this.lock.validate(stamp)) {
                    return ret;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }
    }

    public void flushChangesSync() {
        // Early-exit if there's no work to do
        if (this.mapUpdates.isEmpty()) {
            return;
        }

        final long writeLock =  this.lock.writeLock();

        try {
            // Use a non-allocating iterator if possible, otherwise we're going to hurt
            for (Long2ObjectMap.Entry<V> entry : Long2ObjectMaps.fastIterable(this.mapUpdates)) {
                final long key = entry.getLongKey();
                final V val = entry.getValue();

                if (val == null) {
                    this.mapLive.remove(key);
                } else {
                    this.mapLive.put(key, val);
                }
            }
        } finally {
            this.lock.unlockWrite(writeLock);
        }

        this.mapUpdates.clear();
    }
}