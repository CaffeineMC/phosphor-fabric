package net.caffeinemc.phosphor.common.chunk.light;

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

    void setHeight(long chunkPos, int y);
}
