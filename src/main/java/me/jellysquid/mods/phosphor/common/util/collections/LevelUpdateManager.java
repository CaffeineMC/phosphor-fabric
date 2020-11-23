package me.jellysquid.mods.phosphor.common.util.collections;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;

import java.util.Arrays;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;
import static it.unimi.dsi.fastutil.HashCommon.maxFill;

public class LevelUpdateManager {
    private static final float TABLE_LOAD_FACTOR = Hash.FAST_LOAD_FACTOR;
    private static final int TABLE_INITIAL_SIZE = 756;

    private static final byte ABSENT_VALUE_BYTE = (byte) -1;
    private static final int ABSENT_VALUE_INT = Byte.toUnsignedInt(ABSENT_VALUE_BYTE);

    // Avoid needing to special case null keys (0) everywhere by flipping an unused bit in the position keys
    private static final long KEY_FUZZ = 1 << 10;

    private long[] tableKey;
    private long[] tableValueLevels;
    private byte[] tableValuePendingUpdate;

    private int tableMask;
    private int tableN;
    private int tableMaxFill;
    private int tableSize;

    private final LevelUpdateQueue[] queues;
    private int enqueued;

    public LevelUpdateManager(int levelCount) {
        this.queues = new LevelUpdateQueue[levelCount];

        for (int i = 0; i < levelCount; i++) {
            this.queues[i] = new LevelUpdateQueue();
        }

        this.tableN = arraySize(TABLE_INITIAL_SIZE, TABLE_LOAD_FACTOR);
        this.tableMask = this.tableN - 1;
        this.tableMaxFill = maxFill(this.tableN, TABLE_LOAD_FACTOR);
        this.tableKey = new long[this.tableN];
        this.tableValueLevels = new long[this.tableN];
        this.tableValuePendingUpdate = new byte[this.tableN];
    }

    public int getPendingUpdate(long id) {
        int idx = this.findTableIndex(id | KEY_FUZZ);

        if (idx < 0) {
            return ABSENT_VALUE_INT;
        }

        return Byte.toUnsignedInt(this.tableValuePendingUpdate[idx]);
    }

    public int removePendingUpdate(long id) {
        int idx = this.findTableIndex(id | KEY_FUZZ);

        if (idx < 0) {
            return ABSENT_VALUE_INT;
        }

        byte val = this.tableValuePendingUpdate[idx];
        this.tableValuePendingUpdate[idx] = ABSENT_VALUE_BYTE;

        return Byte.toUnsignedInt(val);
    }

    public boolean removeAndDeque(long id, int level) {
        int idx = this.findTableIndex(id | KEY_FUZZ);

        if (idx < 0) {
            return false;
        }

        this.tableValuePendingUpdate[idx] = ABSENT_VALUE_BYTE;
        this.tableValueLevels[idx] &= ~(1L << level);

        return true;
    }

    public long next(int level) {
        LevelUpdateQueue queue = this.queues[level];

        while (!queue.isEmpty()) {
            long pos = queue.dequeue();

            if (this.dequeue(pos, level)) {
                return pos;
            }
        }

        return Long.MIN_VALUE;
    }

    public void clear() {
        if (this.enqueued > 0) {
            for (LevelUpdateQueue queue : this.queues) {
                queue.clear();
            }

            this.enqueued = 0;
        }

        if (this.tableSize != 0) {
            this.tableSize = 0;

            Arrays.fill(this.tableKey, 0);
        }
    }

    public boolean enqueue(long id, int pendingUpdate, int targetLevel) {
        int idx = this.findTableIndex(id | KEY_FUZZ);

        long mask = 1L << targetLevel;

        if (idx < 0) {
            final int pos = -idx - 1;

            this.tableKey[pos] = id | KEY_FUZZ;
            this.tableValuePendingUpdate[pos] = (byte) pendingUpdate;
            this.tableValueLevels[pos] = mask;

            if (this.tableSize++ >= this.tableMaxFill) {
                this.grow();
            }
        } else {
            long prevTargetLevel = this.tableValueLevels[idx];

            if ((prevTargetLevel & mask) != 0) {
                return false;
            }

            this.tableValuePendingUpdate[idx] = (byte) pendingUpdate;
            this.tableValueLevels[idx] = prevTargetLevel | mask;
        }

        this.queues[targetLevel].enqueue(id);
        this.enqueued++;

        return true;
    }

    public boolean dequeue(long id, int level) {
        int idx = this.findTableIndex(id | KEY_FUZZ);

        if (idx < 0) {
            return false;
        }

        long mask = 1L << level;
        long prevLevel = this.tableValueLevels[idx];

        if ((prevLevel & mask) == 0) {
            return false;
        }

        this.tableValueLevels[idx] = prevLevel & ~mask;
        this.enqueued--;

        return true;
    }

    public boolean isQueueEmpty(int level) {
        return this.queues[level].isEmpty();
    }

    public int getPendingUpdateCount() {
        return this.enqueued;
    }

    private int findTableIndex(final long k) {
        final long[] key = this.tableKey;

        int pos = (int) HashCommon.mix(k) & this.tableMask;
        long curr = key[pos];

        while (curr != 0) {
            if (k == curr) {
                return pos;
            }

            pos = pos + 1 & this.tableMask;
            curr = key[pos];
        }

        return -(pos + 1);
    }

    private void grow() {
        this.rehash(arraySize(this.tableSize, TABLE_LOAD_FACTOR));
    }

    private void rehash(final int newN) {
        final long[] key = this.tableKey;
        final long[] valueLevels = this.tableValueLevels;
        final byte[] valuePendingUpdate = this.tableValuePendingUpdate;

        final int mask = newN - 1;

        final long[] newKey = new long[newN];
        final long[] newValueLevels = new long[newN];
        final byte[] newValuePendingUpdate = new byte[newN];

        int i = this.tableN;

        int pos;
        int size = this.tableSize;

        while (size-- != 0) {
            do {
                i--;
            } while (key[i] == 0);

            pos = (int) HashCommon.mix(key[i]) & mask;

            while (newKey[pos] != 0) {
                pos = pos + 1 & mask;
            }

            newKey[pos] = key[i];
            newValueLevels[pos] = valueLevels[i];
            newValuePendingUpdate[pos] = valuePendingUpdate[i];
        }

        this.tableN = newN;
        this.tableMask = mask;
        this.tableMaxFill = maxFill(this.tableN, TABLE_LOAD_FACTOR);
        this.tableKey = newKey;
        this.tableValueLevels = newValueLevels;
        this.tableValuePendingUpdate = newValuePendingUpdate;
    }
}
