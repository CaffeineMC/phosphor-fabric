package me.jellysquid.mods.phosphor.mixin.chunk;

import me.jellysquid.mods.phosphor.common.chunk.light.IReadonly;
import net.minecraft.world.chunk.ChunkNibbleArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * An optimized implementation of ChunkNibbleArray which uses bit-banging instead of a conditional to select
 * the right bit index of a nibble.
 */
@Mixin(ChunkNibbleArray.class)
public abstract class MixinChunkNibbleArray implements IReadonly {
    @Shadow
    protected byte[] byteArray;

    /**
     * @reason Avoid an additional branch.
     * @author JellySquid
     */
    @Overwrite
    private int get(int idx) {
        byte[] arr = this.byteArray;

        if (arr == null) {
            return 0;
        }

        int byteIdx = idx >> 1;
        int shift = (idx & 1) << 2;

        return (arr[byteIdx] >>> shift) & 15;
    }

    /**
     * @reason Avoid an additional branch.
     * @author JellySquid
     */
    @Overwrite
    private void set(int idx, int value) {
        if (this.isReadonly()) {
            throw new UnsupportedOperationException("Cannot modify readonly ChunkNibbleArray");
        }

        byte[] arr = this.byteArray;

        if (arr == null) {
            this.byteArray = (arr = new byte[2048]);
        }

        int byteIdx = idx >> 1;
        int shift = (idx & 1) << 2;

        arr[byteIdx] = (byte) ((arr[byteIdx] & ~(15 << shift))
                | ((value & 15) << shift));
    }

    @Override
    public boolean isReadonly() {
        return false;
    }
}
