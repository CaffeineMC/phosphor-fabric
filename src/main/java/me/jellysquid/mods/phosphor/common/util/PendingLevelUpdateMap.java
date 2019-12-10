package me.jellysquid.mods.phosphor.common.util;

import it.unimi.dsi.fastutil.Hash;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;
import static it.unimi.dsi.fastutil.HashCommon.maxFill;

public class PendingLevelUpdateMap implements Hash {
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
    protected transient int n;

    /**
     * Threshold after which we rehash. It must be the table size times {@link #f}.
     */
    protected transient int maxFill;

    /**
     * We never resize below this threshold, which is the construction-time {#n}.
     */
    protected final transient int minN;

    /**
     * Number of entries in the set (including the key zero, if present).
     */
    protected int size;

    /**
     * The acceptable load factor.
     */
    protected final float f;

    public PendingLevelUpdateMap(final int expected, final float f) {
        if (f <= 0 || f > 1) {
            throw new IllegalArgumentException("Load factor must be greater than 0 and smaller than or equal to 1");
        }
        if (expected < 0) {
            throw new IllegalArgumentException("The expected number of elements must be nonnegative");
        }
        this.f = f;
        minN = n = arraySize(expected, f);
        mask = n - 1;
        maxFill = maxFill(n, f);
        key = new long[n + 1];
        value = new int[n + 1];
    }

    private int realSize() {
        return containsNullKey ? size - 1 : size;
    }

    private int find(final long k) {
        if (((k) == (0))) {
            return containsNullKey ? n : -(n + 1);
        }
        long curr;
        final long[] key = this.key;
        int pos;
        // The starting point.
        if (((curr = key[pos = (int) it.unimi.dsi.fastutil.HashCommon.mix((k)) & mask]) == (0))) {
            return -(pos + 1);
        }
        if (((k) == (curr))) {
            return pos;
        }
        // There's always an unused entry.
        while (true) {
            if (((curr = key[pos = (pos + 1) & mask]) == (0))) {
                return -(pos + 1);
            }
            if (((k) == (curr))) {
                return pos;
            }
        }
    }

    private void insert(final int pos, final long k, final int v) {
        if (pos == n) {
            containsNullKey = true;
        }
        key[pos] = k;
        value[pos] = v;
        if (size++ >= maxFill) {
            rehash(arraySize(size + 1, f));
        }
    }


    public boolean replace(final long k, final int oldValue, final int v) {
        final int pos = find(k);
        if (pos < 0 || !((oldValue) == (value[pos]))) {
            return false;
        }
        value[pos] = v;
        return true;
    }

    public int replace(final long k, final int v) {
        final int pos = find(k);
        if (pos < 0) {
            return Integer.MIN_VALUE;
        }
        final int oldValue = value[pos];
        value[pos] = v;
        return oldValue;
    }

    protected void rehash(final int newN) {
        final long[] key = this.key;
        final int[] value = this.value;
        final int mask = newN - 1; // Note that this is used by the hashing macro
        final long[] newKey = new long[newN + 1];
        final int[] newValue = new int[newN + 1];
        int i = n, pos;
        for (int j = realSize(); j-- != 0; ) {
            while (((key[--i]) == (0))) {
            }
            if (!((newKey[pos = (int) it.unimi.dsi.fastutil.HashCommon.mix((key[i])) & mask]) == (0))) {
                while (!((newKey[pos = (pos + 1) & mask]) == (0))) {
                }
            }
            newKey[pos] = key[i];
            newValue[pos] = value[i];
        }
        newValue[newN] = value[n];
        n = newN;
        this.mask = mask;
        maxFill = maxFill(n, f);
        this.key = newKey;
        this.value = newValue;
    }

    public boolean putIfAbsentFast(final long k, final int v) {
        final int pos = find(k);

        if (pos >= 0) {
            int ret = value[pos];

            if (ret == Integer.MIN_VALUE) {
                value[pos] = v;
            } else {
                return false;
            }

            return true;
        }

        insert(-pos - 1, k, v);

        return true;
    }
}
