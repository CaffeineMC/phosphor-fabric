package me.jellysquid.mods.phosphor.common.util;

import java.util.ArrayDeque;
import java.util.Deque;

//Implement own queue with pooled segments to reduce allocation costs and reduce idle memory footprint
public class PooledLongQueue {
    private static final int CACHED_QUEUE_SEGMENTS_COUNT = 1 << 12;
    private static final int QUEUE_SEGMENT_SIZE = 1 << 10;

    private final LongQueueSegmentPool pool;

    private PooledLongQueueSegment cur, last;

    private int size = 0;

    public PooledLongQueue(LongQueueSegmentPool pool) {
        this.pool = pool;
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.cur == null;
    }

    public void add(final long val) {
        if (this.cur == null) {
            this.cur = this.last = this.pool.acquire();
        }

        if (this.last.index == QUEUE_SEGMENT_SIZE) {
            PooledLongQueueSegment ret = this.last.next = this.last.pool.acquire();
            ret.longArray[ret.index++] = val;

            this.last = ret;
        } else {
            this.last.longArray[this.last.index++] = val;
        }

        ++this.size;
    }

    public LongQueueIterator iterator() {
        return new LongQueueIterator(this.cur);
    }

    public void clear() {
        PooledLongQueueSegment segment = this.cur;

        while (segment != null) {
            PooledLongQueueSegment next = segment.next;
            segment.release();
            segment = next;
        }

        this.size = 0;
        this.cur = null;
        this.last = null;
    }

    public class LongQueueIterator {
        private PooledLongQueueSegment cur;
        private long[] curArray;

        private int index, capacity;

        private LongQueueIterator(PooledLongQueueSegment cur) {
            this.cur = cur;

            if (this.cur != null) {
                this.curArray = cur.longArray;
                this.capacity = cur.index;
            }
        }

        public boolean hasNext() {
            return this.cur != null;
        }

        public long next() {
            final long ret = this.curArray[this.index++];

            if (this.index == this.capacity) {
                this.index = 0;

                this.cur = this.cur.next;

                if (this.cur != null) {
                    this.curArray = this.cur.longArray;
                    this.capacity = this.cur.index;
                }
            }

            return ret;
        }

        public void dispose() {
            PooledLongQueue.this.clear();
        }
    }

    public static class LongQueueSegmentPool {
        private final Deque<PooledLongQueueSegment> segmentPool = new ArrayDeque<>();

        private PooledLongQueueSegment acquire() {
            if (this.segmentPool.isEmpty()) {
                return new PooledLongQueueSegment(this);
            }

            return this.segmentPool.pop();
        }

        private void release(PooledLongQueueSegment segment) {
            if (this.segmentPool.size() < CACHED_QUEUE_SEGMENTS_COUNT) {
                this.segmentPool.push(segment);
            }
        }
    }

    private static class PooledLongQueueSegment {
        private final long[] longArray = new long[QUEUE_SEGMENT_SIZE];
        private int index = 0;
        private PooledLongQueueSegment next;
        private LongQueueSegmentPool pool;

        private PooledLongQueueSegment(LongQueueSegmentPool pool) {
            this.pool = pool;
        }

        private void release() {
            this.index = 0;
            this.next = null;

            this.pool.release(this);
        }
    }

}
