package me.jellysquid.mods.phosphor.common.util.chunk.light;

public class EmptyChunkNibbleArray extends ReadonlyChunkNibbleArray {
    public EmptyChunkNibbleArray() {
    }

    @Override
    public byte[] asByteArray() {
        return new byte[2048];
    }
}
