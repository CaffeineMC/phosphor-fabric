package me.jellysquid.mods.phosphor.common.util.chunk.light;

import net.minecraft.world.chunk.ChunkNibbleArray;

public class SkyLightChunkNibbleArray extends ReadonlyChunkNibbleArray {
    public SkyLightChunkNibbleArray(final byte[] inheritedLightmap) {
        super(inheritedLightmap);
    }

    public SkyLightChunkNibbleArray(final ChunkNibbleArray inheritedLightmap) {
        this(inheritedLightmap.asByteArray());
    }

    @Override
    protected int getIndex(final int x, final int y, final int z) {
        return super.getIndex(x, 0, z);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public byte[] asByteArray() {
        byte[] byteArray = new byte[2048];

        for(int i = 0; i < 16; ++i) {
            System.arraycopy(this.byteArray, 0, byteArray, i * 128, 128);
        }

        return byteArray;
    }
}
