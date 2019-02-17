package me.jellysquid.mods.phosphor.client;

import me.jellysquid.mods.phosphor.common.PhosphorProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IThreadListener;

public class PhosphorProxyClient implements PhosphorProxy {
    @Override
    public IThreadListener getMinecraftThread() {
        return Minecraft.getMinecraft();
    }
}
