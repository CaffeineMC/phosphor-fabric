package me.jellysquid.mods.phosphor.common.chunk.level;

public interface LevelUpdateListener {
    void onPendingUpdateRemoved(long key);

    void onPendingUpdateAdded(long key);
}
