package me.jellysquid.mods.phosphor.common.chunk.light;

public interface LightProviderUpdateTracker {
    /**
     * Discards all pending updates for the specified chunk section.
     */
    void cancelUpdatesForChunk(long sectionPos);
}
