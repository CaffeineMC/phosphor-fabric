package me.jellysquid.mods.phosphor.common.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public interface SkyLightStorageDataAccess {
    /**
     * Bridge method to SkyLightStorageData#defaultHeight().
     */
    int getDefaultHeight();

    /**
     * Bridge method to SkyLightStorageData#heightMap().
     */
    Long2IntOpenHashMap getHeightMap();
}
