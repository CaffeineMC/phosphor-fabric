package me.jellysquid.mods.phosphor.common.util.collections;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;

import java.util.Arrays;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;
import static it.unimi.dsi.fastutil.HashCommon.maxFill;

public class PendingLevelUpdateTracker {
    /**
     * The initial size the work queue was initialized to. The queue size can never be reduced below this
     * size.
     */
    private final int initialQueueSize;

    /**
     * The current work queue.
     */
    public long[] queue;

    /**
     * The read/write indices of the current work queue.
     */
    public int queueReadIdx, queueWriteIdx;

    /**
     * The array of keys.
     */
    protected transient long[] key;

    /**
     * The array of values.
     */
    protected transient int[] value;

    /**
     * The mask for wrapping a position counter.
     */
    protected transient int mask;

    /**
     * Whether this map contains the key zero.
     */
    protected transient boolean containsNullKey;

    /**
     * The current table size.
     */
    protected transient int tableSize;

    /**
     * Threshold after which we rehash. It must be the table size times {@link #loadFactor}.
     */
    protected transient int maxFill;

    /**
     * We never resize below this threshold, which is the construction-time {#n}.
     */
    protected final transient int minTableSize;

    /**
     * Number of entries in the set (including the key zero, if present).
     */
    protected int size;

    /**
     * The acceptable load factor.
     */
    protected final float loadFactor;

    public PendingLevelUpdateTracker(int initLevelCapacity, int size) {
        this.initialQueueSize = size;

        this.clear();

        float loadFactor = Hash.FAST_LOAD_FACTOR;

        this.loadFactor = loadFactor;
        this.tableSize = arraySize(initLevelCapacity, loadFactor);
        this.minTableSize = this.tableSize;
        this.mask = this.tableSize - 1;
        this.maxFill = maxFill(this.tableSize, loadFactor);
        this.key = new long[this.tableSize + 1];
        this.value = new int[this.tableSize + 1];
    }

    public void add(long pos) {
        final int index = this.getQueueIndex(pos);

        if (index >= 0) {
            int ret = this.value[index];

            if (ret == Integer.MIN_VALUE) {
                this.value[index] = this.queueWriteIdx;
            } else {
                return;
            }
        } else {
            this.insertIntoQueue(-index - 1, pos, this.queueWriteIdx);
        }

        this.queue[this.queueWriteIdx++] = pos;

        if (this.queueWriteIdx >= this.queue.length) {
            this.resize(this.queue.length * 2);
        }
    }

    public void remove(long pos) {
        final int index = this.getQueueIndex(pos);

        if (index >= 0) {
            final int oldValue = this.value[index];
            this.value[index] = Integer.MIN_VALUE;

            if (oldValue != Integer.MIN_VALUE) {
                this.queue[oldValue] = Integer.MIN_VALUE;
            }
        }
    }

    public boolean consume(long pos, int idx) {
        final int index = this.getQueueIndex(pos);

        if (index < 0 || idx != this.value[index]) {
            return false;
        }

        this.value[index] = Integer.MIN_VALUE;

        return true;
    }

    private void resize(int len) {
        this.queue = Arrays.copyOf(this.queue, len);
    }

    private int realSize() {
        return this.containsNullKey ? this.size - 1 : this.size;
    }

    private int getQueueIndex(final long k) {
        if (k == 0) {
            return this.containsNullKey ? this.tableSize : -(this.tableSize + 1);
        }

        long curr;

        final long[] key = this.key;
        int pos;

        // The starting point.
        if ((curr = key[pos = (int) HashCommon.mix(k) & this.mask]) == 0) {
            return -(pos + 1);
        }

        if (k == curr) {
            return pos;
        }

        // There's always an unused entry.
        while (true) {
            if ((curr = key[pos = pos + 1 & this.mask]) == 0) {
                return -(pos + 1);
            }

            if (k == curr) {
                return pos;
            }
        }
    }

    private void insertIntoQueue(final int pos, final long k, final int v) {
        if (pos == this.tableSize) {
            this.containsNullKey = true;
        }

        this.key[pos] = k;
        this.value[pos] = v;

        if (this.size++ >= this.maxFill) {
            this.rehashQueue(arraySize(this.size + 1, this.loadFactor));
        }
    }

    protected void rehashQueue(final int newN) {
        final long[] key = this.key;
        final int[] value = this.value;

        final int mask = newN - 1; // Note that this is used by the hashing macro

        final long[] newKey = new long[newN + 1];
        final int[] newValue = new int[newN + 1];

        int i = this.tableSize, pos;

        for (int j = this.realSize(); j-- != 0; ) {
            do {
                i--;
            } while (key[i] == 0);

            if (!(newKey[pos = (int) HashCommon.mix(key[i]) & mask] == 0)) {
                do {
                    pos = pos + 1 & mask;
                } while (!(newKey[pos] == 0));
            }

            newKey[pos] = key[i];
            newValue[pos] = value[i];
        }

        newValue[newN] = value[this.tableSize];

        this.tableSize = newN;
        this.mask = mask;
        this.maxFill = maxFill(this.tableSize, this.loadFactor);
        this.key = newKey;
        this.value = newValue;
    }

    public void clear() {
        this.queueReadIdx = 0;
        this.queueWriteIdx = 0;

        if (this.queue == null || this.queue.length > this.initialQueueSize) {
            this.queue = new long[this.initialQueueSize];
        }

        if (this.size != 0) {
            this.size = 0;
            this.containsNullKey = false;

            Arrays.fill(this.key, 0);
        }
    }
}
