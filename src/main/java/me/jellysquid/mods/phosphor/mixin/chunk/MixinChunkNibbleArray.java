package me.jellysquid.mods.phosphor.mixin.chunk;

import net.minecraft.world.chunk.ChunkNibbleArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * An optimized implementation of ChunkNibbleArray which uses bit-banging instead of a conditional to select
 * the right bit index of a nibble.
 * <p>
 * TODO: Is it if faster to always initialize this with a dummy array and then copy-on-write?
 */
@Mixin(ChunkNibbleArray.class)
public abstract class MixinChunkNibbleArray {
    @Shadow
    protected byte[] byteArray;

    /**
     * @reason Avoid an additional branch.
     * @author JellySquid
     */
    @Overwrite
    private int get(int idx) {
        if (this.byteArray == null) {
            return 0;
        }

        int nibbleIdx = idx & 1;
        int byteIdx = idx >> 1;
        int shift = nibbleIdx << 2;

        return (this.byteArray[byteIdx] >>> shift) & 15;
    }

    /**
     * @reason Avoid an additional branch.
     * @author JellySquid
     */
    @Overwrite
    private void set(int idx, int value) {
        if (this.byteArray == null) {
            this.byteArray = new byte[2048];
        }

        int nibbleIdx = idx & 1;
        int byteIdx = idx >> 1;
        int shift = nibbleIdx << 2;

        int b = this.byteArray[byteIdx];
        int ret = (b & ~(15 << shift)) | (value & 15) << shift;

        this.byteArray[byteIdx] = (byte) ret;
    }


}
