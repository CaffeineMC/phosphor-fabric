package me.jellysquid.mods.phosphor.common.chunk.light;

import me.jellysquid.mods.phosphor.common.util.collections.DoubleBufferedLong2IntHashMap;

public interface SkyLightStorageDataAccess {
    /**
     * Bridge method to SkyLightStorageData#defaultHeight().
     */
    int getDefaultHeight();

    /**
     * Returns the height map value for the given block column in the world.
     */
    int getHeight(long pos);

    void updateMinHeight(int y);
}
