package me.jellysquid.mods.phosphor.common.util.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.IReadonly;
import net.minecraft.world.chunk.ChunkNibbleArray;

public class ReadonlyChunkNibbleArray extends ChunkNibbleArray implements IReadonly {
    public ReadonlyChunkNibbleArray() {
    }

    public ReadonlyChunkNibbleArray(byte[] bs) {
        super(bs);
    }

    @Override
    public ChunkNibbleArray copy() {
        return new ChunkNibbleArray(this.asByteArray());
    }

    @Override
    public boolean isReadonly() {
        return true;
    }
}
