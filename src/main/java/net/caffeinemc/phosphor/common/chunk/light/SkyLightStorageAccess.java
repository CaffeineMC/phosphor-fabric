package net.caffeinemc.phosphor.common.chunk.light;

public interface SkyLightStorageAccess extends LightStorageAccess {
    boolean callIsAboveMinHeight(int y);
}
