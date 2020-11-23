package me.jellysquid.mods.phosphor.common.util.collections;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

import java.util.Arrays;

public class PendingLightUpdateQueue {
    private static final int DEFAULT_SIZE = 256;

    private final Long2BooleanMap pointers = new Long2BooleanOpenHashMap(DEFAULT_SIZE, Hash.FAST_LOAD_FACTOR);

    private long[] queue = new long[DEFAULT_SIZE];

    private int queueHead = 0;
    private int queueSize = 0;

    public PendingLightUpdateQueue() {
        this.pointers.defaultReturnValue(false);
    }

    public void add(long pos) {
        if (this.pointers.putIfAbsent(pos, true)) {
            return;
        }

        this.enqueue(pos);
    }

    private void enqueue(long pos) {
        int i = this.queueSize++;

        if (i >= this.queue.length) {
            this.grow();
        }

        this.queue[i] = pos;
    }

    private void grow() {
        this.queue = Arrays.copyOf(this.queue, this.queue.length << 1);
    }

    public boolean remove(long pos) {
        return this.pointers.replace(pos, false);
    }

    public long next() {
        while (!this.isEmpty()) {
            long pos = this.dequeue();

            if (this.remove(pos)) {
                return pos;
            }
        }

        return Long.MIN_VALUE;
    }

    private long dequeue() {
        return this.queue[this.queueHead++];
    }

    public boolean isEmpty() {
        return this.queueHead >= this.queueSize;
    }

    public void clear() {
        this.pointers.clear();
        this.queueSize = 0;
        this.queueHead = 0;
    }
}
