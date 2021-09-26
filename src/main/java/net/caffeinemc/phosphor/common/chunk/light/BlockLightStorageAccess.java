package net.caffeinemc.phosphor.common.chunk.light;

public interface BlockLightStorageAccess extends LightStorageAccess {
    boolean isLightEnabled(long sectionPos);
}
