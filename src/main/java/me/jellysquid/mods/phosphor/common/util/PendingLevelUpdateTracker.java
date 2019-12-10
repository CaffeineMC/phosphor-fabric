package me.jellysquid.mods.phosphor.common.util;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public class PendingLevelUpdateTracker {
    private final int initLevelCapacity;

    public PendingLevelUpdateMap map;
    public long[] queue;

    public int queueReadIdx, queueWriteIdx;
    public int realSize;

    public PendingLevelUpdateTracker(int initLevelCapacity, int size) {
        this.initLevelCapacity = initLevelCapacity;
        this.queue = new long[size];
        this.map = this.createMap();
    }

    private PendingLevelUpdateMap createMap() {
        return new PendingLevelUpdateMap(this.initLevelCapacity, 0.5f);
    }

    public void add(long pos) {
        if (this.map.putIfAbsentFast(pos, this.queueWriteIdx)) {
            this.queue[this.queueWriteIdx++] = pos;
            this.realSize++;

            if (this.queueWriteIdx >= this.queue.length) {
                this.increaseQueueCapacity();
            }
        }
    }

    public void remove(long pos) {
        int idx = this.map.replace(pos, Integer.MIN_VALUE);

        if (idx != Integer.MIN_VALUE) {
            this.realSize--;
            this.queue[idx] = Integer.MIN_VALUE;
        }
    }

    public boolean consume(long pos, int idx) {
        boolean ret = this.map.replace(pos, idx, Integer.MIN_VALUE);

        if (ret) {
            this.realSize--;
        }

        return ret;
    }

    public void clear() {
        this.map = this.createMap();
        this.queueReadIdx = 0;
        this.queueWriteIdx = 0;
        this.realSize = 0;
    }

    public boolean isEmpty() {
        return this.realSize <= 0;
    }

    private void increaseQueueCapacity() {
        long[] old = this.queue;

        this.queue = new long[old.length * 2];

        System.arraycopy(old, 0, this.queue, 0, old.length);
    }

    private static class Map extends Long2IntOpenHashMap {
        public Map(int initialCapacity, float loadFactor) {
            super(initialCapacity, loadFactor);
        }


    }
}
