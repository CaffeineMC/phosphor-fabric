package me.jellysquid.mods.phosphor.common.util.collections;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.concurrent.locks.StampedLock;

/**
 * A double buffered Long->Object hash table which allows for multiple readers to see a consistent view without
 * contention over shared resources. The synchronous (owned) view must be synced using
 * {@link DoubleBufferedLong2IntHashMap#flushChangesSync()} after all desired changes have been made.
 *
 * Methods labeled as synchronous access the owned mutable view of this map which behaves as the back-buffer. When
 * changes are flushed, the front-buffer is locked, written into, and then unlocked. The locking implementation is
 * optimized for (relatively) infrequent flips.
 *
 * {@link Integer#MIN_VALUE} is used to indicate values which are to be removed and cannot be added to the queue.
 */
public class DoubleBufferedLong2IntHashMap {
    // The hash table of entries belonging to the owning thread
    private final Long2IntMap mapPending;

    // The hash table of entries available to other threads
    private final Long2IntMap mapVisible;

    // The map of pending entry updates to be applied to the visible hash table
    private final Long2IntMap mapUpdates;

    // The lock used by other threads to grab values from the visible map asynchronously. This prevents other threads
    // from seeing partial updates while the changes are flushed. The lock implementation is specially selected to
    // optimize for the common case: infrequent writes, very frequent reads.
    private final StampedLock lock = new StampedLock();

    // The pending return value as seen by the owning thread
    private int queuedDefaultReturnValue;

    public DoubleBufferedLong2IntHashMap() {
        this(16, Hash.FAST_LOAD_FACTOR);
    }

    public DoubleBufferedLong2IntHashMap(int capacity, float loadFactor) {
        this.mapPending = new Long2IntOpenHashMap(capacity, loadFactor);
        this.mapVisible = new Long2IntOpenHashMap(capacity, loadFactor);
        this.mapUpdates = new Long2IntOpenHashMap(capacity, loadFactor);
    }

    public void defaultReturnValueSync(int v) {
        this.queuedDefaultReturnValue = v;

        this.mapPending.defaultReturnValue(v);
    }

    public int putSync(long k, int v) {
        if (v == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Value Integer.MIN_VALUE cannot be used");
        }

        this.mapUpdates.put(k, v);

        return this.mapPending.put(k, v);
    }

    public int removeSync(long k) {
        this.mapUpdates.put(k, Integer.MIN_VALUE);

        return this.mapPending.remove(k);
    }

    public int getSync(long k) {
        return this.mapPending.get(k);
    }

    public int getAsync(long k) {
        while (true) {
            final long stamp = this.lock.tryOptimisticRead();

            // Long2IntOpenHashMap is not thread-safe and may throw ArrayIndexOutOfBoundsException when queried in an inconsistent state
            try {
                final int ret = this.mapVisible.get(k);

                if (this.lock.validate(stamp)) {
                    return ret;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }
    }

    /**
     * Flushes all pending changes to the visible hash table seen by outside consumers.
     */
    public void flushChangesSync() {
        // The return value above has to be updated before we try to early-exit
        final long writeLock = this.lock.writeLock();

        try {
            // First, update the return value of the collection
            this.mapVisible.defaultReturnValue(this.queuedDefaultReturnValue);

            // Perform the early-exit check now
            if (this.mapUpdates.isEmpty()) {
                return;
            }

            // Use a non-allocating iterator if possible, otherwise we're going to hurt
            for (Long2IntMap.Entry entry : Long2IntMaps.fastIterable(this.mapUpdates)) {
                final long key = entry.getLongKey();
                final int val = entry.getIntValue();

                // MIN_VALUE indicates that the value should be removed instead
                if (val == Integer.MIN_VALUE) {
                    this.mapVisible.remove(key);
                } else {
                    this.mapVisible.put(key, val);
                }
            }
        } finally {
            this.lock.unlockWrite(writeLock);
        }

        this.mapUpdates.clear();
    }

    public Long2IntOpenHashMap createSyncView() {
        return new Long2IntOpenHashMap() {
            @Override
            public int size() {
                return DoubleBufferedLong2IntHashMap.this.mapPending.size();
            }

            @Override
            public void defaultReturnValue(int rv) {
                DoubleBufferedLong2IntHashMap.this.defaultReturnValueSync(rv);
            }

            @Override
            public int defaultReturnValue() {
                return DoubleBufferedLong2IntHashMap.this.mapPending.defaultReturnValue();
            }

            @Override
            public boolean containsKey(long key) {
                return DoubleBufferedLong2IntHashMap.this.mapPending.containsKey(key);
            }

            @Override
            public boolean containsValue(int value) {
                return DoubleBufferedLong2IntHashMap.this.mapPending.containsValue(value);
            }

            @Override
            public int get(long key) {
                return DoubleBufferedLong2IntHashMap.this.getSync(key);
            }

            @Override
            public int put(long key, int value) {
                return DoubleBufferedLong2IntHashMap.this.putSync(key, value);
            }

            @Override
            public int remove(long key) {
                return DoubleBufferedLong2IntHashMap.this.removeSync(key);
            }

            @Override
            public boolean isEmpty() {
                return DoubleBufferedLong2IntHashMap.this.mapPending.isEmpty();
            }
        };
    }
}