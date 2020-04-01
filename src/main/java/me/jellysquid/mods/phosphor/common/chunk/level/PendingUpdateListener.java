package me.jellysquid.mods.phosphor.common.chunk.level;

public interface PendingUpdateListener {
    void onPendingUpdateRemoved(long key);

    void onPendingUpdateAdded(long key);
}
