package me.jellysquid.mods.phosphor.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

public interface ILightingEngine {
    void scheduleLightUpdate(EnumSkyBlock lightType, BlockPos pos, boolean isTickEvent);

    void processLightUpdates(boolean isTickEvent);

    void processLightUpdatesForType(EnumSkyBlock lightType, boolean isTickEvent);
}
