package me.jellysquid.mods.phosphor.mod.server;

import me.jellysquid.mods.phosphor.mod.PhosphorProxy;
import net.minecraft.util.IThreadListener;

public class PhosphorProxyServer implements PhosphorProxy {
    @Override
    public IThreadListener getMinecraftThread() {
        return null;
    }
}
