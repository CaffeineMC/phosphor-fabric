package me.jellysquid.mods.phosphor.common.util.collections;

public class PendingLevelUpdateTracker {
    private final int initLevelCapacity;

    public PendingLevelUpdateMap map;
    public long[] queue;

    public int queueReadIdx, queueWriteIdx;

    public PendingLevelUpdateTracker(int initLevelCapacity, int size) {
        this.initLevelCapacity = initLevelCapacity;
        this.queue = new long[size];
        this.map = new PendingLevelUpdateMap(this.initLevelCapacity, 0.5f);
    }

    public void add(long pos) {
        if (this.map.putIfAbsentFast(pos, this.queueWriteIdx)) {
            this.queue[this.queueWriteIdx++] = pos;

            if (this.queueWriteIdx >= this.queue.length) {
                this.increaseQueueCapacity();
            }
        }
    }

    public void remove(long pos) {
        int idx = this.map.replace(pos, Integer.MIN_VALUE);

        if (idx != Integer.MIN_VALUE) {
            this.queue[idx] = Integer.MIN_VALUE;
        }
    }

    public boolean consume(long pos, int idx) {
        return this.map.replace(pos, idx, Integer.MIN_VALUE);
    }

    public void clear() {
        this.map = new PendingLevelUpdateMap(this.initLevelCapacity, 0.5f);
        this.queueReadIdx = 0;
        this.queueWriteIdx = 0;
    }

    private void increaseQueueCapacity() {
        long[] old = this.queue;

        this.queue = new long[old.length * 2];

        System.arraycopy(old, 0, this.queue, 0, old.length);
    }
}
