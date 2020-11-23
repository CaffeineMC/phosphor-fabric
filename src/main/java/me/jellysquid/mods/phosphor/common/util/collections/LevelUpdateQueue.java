package me.jellysquid.mods.phosphor.common.util.collections;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

import java.util.Arrays;

public class LevelUpdateQueue {
    private static final int DEFAULT_SIZE = 256;

    private long[] queue = new long[DEFAULT_SIZE];

    private int queueHead = 0;
    private int queueSize = 0;

    protected void enqueue(long pos) {
        int i = this.queueSize++;

        if (i >= this.queue.length) {
            this.grow();
        }

        this.queue[i] = pos;
    }

    private void grow() {
        this.queue = Arrays.copyOf(this.queue, this.queue.length << 1);
    }

    protected long dequeue() {
        return this.queue[this.queueHead++];
    }

    public boolean isEmpty() {
        return this.queueHead >= this.queueSize;
    }

    public void clear() {
        this.queueSize = 0;
        this.queueHead = 0;
    }
}
