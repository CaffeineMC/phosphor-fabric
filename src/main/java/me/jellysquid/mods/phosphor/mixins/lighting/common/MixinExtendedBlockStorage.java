package me.jellysquid.mods.phosphor.mixins.lighting.common;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ExtendedBlockStorage.class)
public class MixinExtendedBlockStorage {
    /**
     * @author Angeline
     * @reason Always send chunks to the client so non-trivial lighting is accounted for. This incurs a small increase
     * in network traffic, but Minecraft compresses most of that away.
     */
    @Overwrite
    public boolean isEmpty() {
        return false;
    }
}
