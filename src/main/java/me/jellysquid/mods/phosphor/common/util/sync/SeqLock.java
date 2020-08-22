package me.jellysquid.mods.phosphor.common.util.sync;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A (very simple) implementation of a Sequence Lock. This lock has very good performance characteristics when reads
 * are orders of magnitudes more frequent than writes.
 *
 * https://en.wikipedia.org/wiki/Seqlock
 */
@SuppressWarnings("NonAtomicOperationOnVolatileField")
public class SeqLock {
    private final Lock writeLock = new ReentrantLock();

    // We don't depend on extra features provided by AtomicLong, so use a volatile integer to avoid
    // the overhead of de-referencing an AtomicLong
    private volatile long status;

    public long readBegin() {
        while (true) {
            long current = this.status;

            // This check will fail if status is an odd integer, enabling us to very quickly check to see
            // the lock is possessed by a writer since a lock/unlock cycle will always add 2 ticks. If the
            // status is odd, that means that a lock is currently being held.
            if ((current & 1) == 0) {
                return current;
            }

            Lock writeLock = this.writeLock;
            writeLock.lock();
            writeLock.unlock();
        }
    }

    public boolean shouldRetryRead(long current) {
        return current != this.status;
    }

    public void writeLock() {
        this.writeLock.lock();
        this.status++;
    }

    public void writeUnlock() {
        this.status++;
        this.writeLock.unlock();
    }
}