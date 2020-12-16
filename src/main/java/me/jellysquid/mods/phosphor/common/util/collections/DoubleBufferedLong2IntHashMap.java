package me.jellysquid.mods.phosphor.common.util.collections;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.*;

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
    // The map of pending entry updates to be applied to the visible hash table
    private final Long2IntMap mapPending;

    // The hash table of entries belonging to the owning thread
    private Long2IntMap mapLocal;

    // The hash table of entries available to other threads
    private Long2IntMap mapShared;

    // The lock used by other threads to grab values from the visible map asynchronously. This prevents other threads
    // from seeing partial updates while the changes are flushed. The lock implementation is specially selected to
    // optimize for the common case: infrequent writes, very frequent reads.
    private final StampedLock lock = new StampedLock();

    public DoubleBufferedLong2IntHashMap() {
        this(16, Hash.FAST_LOAD_FACTOR);
    }

    public DoubleBufferedLong2IntHashMap(int capacity, float loadFactor) {
        this.mapLocal = new Long2IntOpenHashMap(capacity, loadFactor);
        this.mapShared = new Long2IntOpenHashMap(capacity, loadFactor);
        this.mapPending = new Long2IntOpenHashMap(capacity, loadFactor);
    }

    public void defaultReturnValueSync(int v) {
        this.mapLocal.defaultReturnValue(v);
    }

    public int putSync(long k, int v) {
        if (v == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Value Integer.MIN_VALUE cannot be used");
        }

        this.mapPending.put(k, v);

        return this.mapLocal.put(k, v);
    }

    public int removeSync(long k) {
        this.mapPending.put(k, Integer.MIN_VALUE);

        return this.mapLocal.remove(k);
    }

    public int getSync(long k) {
        return this.mapLocal.get(k);
    }

    public int getAsync(long k) {
        long stamp;
        int ret = Integer.MIN_VALUE;

        do {
            stamp = this.lock.tryOptimisticRead();

            try {
                ret = this.mapShared.get(k);
            } catch (ArrayIndexOutOfBoundsException ignored) { } // Swallow memory errors on failed optimistic reads
        } while (!this.lock.validate(stamp));

        return ret;
    }

    /**
     * Flushes all pending changes to the visible hash table seen by outside consumers.
     */
    public void flushChangesSync() {
        // Early-exit if there's no work to do
        if (this.mapPending.isEmpty()) {
            return;
        }

        // Swap the local and shared tables immediately, and then block the writer thread while we finish copying
        this.swapTables();

        // Use a non-allocating iterator if possible, otherwise we're going to hurt
        for (Long2IntMap.Entry entry : Long2IntMaps.fastIterable(this.mapPending)) {
            final long key = entry.getLongKey();
            final int val = entry.getIntValue();

            // MIN_VALUE indicates that the value should be removed instead
            if (val == Integer.MIN_VALUE) {
                this.mapLocal.remove(key);
            } else {
                this.mapLocal.put(key, val);
            }
        }

        this.mapLocal.defaultReturnValue(this.mapShared.defaultReturnValue());

        this.mapPending.clear();
    }

    private void swapTables() {
        final long writeLock =  this.lock.writeLock();

        Long2IntMap mapShared = this.mapLocal;
        Long2IntMap mapLocal = this.mapShared;

        this.mapShared = mapShared;
        this.mapLocal = mapLocal;

        this.lock.unlockWrite(writeLock);
    }

    public Long2IntOpenHashMap createSyncView() {
        return new Long2IntOpenHashMap() {
            @Override
            public int size() {
                return DoubleBufferedLong2IntHashMap.this.mapLocal.size();
            }

            @Override
            public void defaultReturnValue(int rv) {
                DoubleBufferedLong2IntHashMap.this.defaultReturnValueSync(rv);
            }

            @Override
            public int defaultReturnValue() {
                return DoubleBufferedLong2IntHashMap.this.mapLocal.defaultReturnValue();
            }

            @Override
            public boolean containsKey(long key) {
                return DoubleBufferedLong2IntHashMap.this.mapLocal.containsKey(key);
            }

            @Override
            public boolean containsValue(int value) {
                return DoubleBufferedLong2IntHashMap.this.mapLocal.containsValue(value);
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
                return DoubleBufferedLong2IntHashMap.this.mapLocal.isEmpty();
            }
        };
    }
}