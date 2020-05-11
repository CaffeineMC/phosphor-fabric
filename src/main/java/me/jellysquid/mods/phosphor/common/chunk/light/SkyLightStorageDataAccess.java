package me.jellysquid.mods.phosphor.common.chunk.light;

import me.jellysquid.mods.phosphor.common.util.collections.DoubleBufferedLong2IntHashMap;

public interface SkyLightStorageDataAccess {
    /**
     * Bridge method to SkyLightStorageData#defaultHeight().
     */
    int getDefaultHeight();

    /**
     * Bridge method to SkyLightStorageData#heightMap().
     * @return
     */
    DoubleBufferedLong2IntHashMap getHeightMap();
}
