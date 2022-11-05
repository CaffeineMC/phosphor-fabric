package net.caffeinemc.phosphor.common.util;

import java.util.function.Supplier;

import net.minecraft.util.profiler.Profiler;

public interface IProfiling {
    void setProfiler(Supplier<Profiler> profiler);
}
