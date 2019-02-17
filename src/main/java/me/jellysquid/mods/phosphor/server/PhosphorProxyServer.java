package me.jellysquid.mods.phosphor.server;

import me.jellysquid.mods.phosphor.common.PhosphorProxy;
import net.minecraft.util.IThreadListener;

public class PhosphorProxyServer implements PhosphorProxy {
    @Override
    public IThreadListener getMinecraftThread() {
        return null;
    }
}
