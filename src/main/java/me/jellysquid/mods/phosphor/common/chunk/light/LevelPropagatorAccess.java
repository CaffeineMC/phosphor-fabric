package me.jellysquid.mods.phosphor.common.chunk.light;

public interface LevelPropagatorAccess {
    void invokePropagateLevel(long sourceId, long targetId, int level, boolean decrease);

    void propagateLevel(long sourceId, long targetId, boolean decrease);

    void checkForUpdates();
}
