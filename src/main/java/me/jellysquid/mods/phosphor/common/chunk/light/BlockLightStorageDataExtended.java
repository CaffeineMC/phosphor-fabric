package me.jellysquid.mods.phosphor.common.chunk.light;

public interface BlockLightStorageDataExtended {
    /**
     * Marks this instance as a shared copy which cannot be directly written into.
     */
    void makeSharedCopy();
}
