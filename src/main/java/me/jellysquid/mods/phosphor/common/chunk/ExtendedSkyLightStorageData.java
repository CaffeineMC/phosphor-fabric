package me.jellysquid.mods.phosphor.common.chunk;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public interface ExtendedSkyLightStorageData {
    /**
     * Bridge method to SkyLightStorageData#defaultHeight().
     */
    int bridge$defaultHeight();

    /**
     * Bridge method to SkyLightStorageData#heightMap().
     */
    Long2IntOpenHashMap bridge$heightMap();
}
