package me.jellysquid.mods.phosphor.common.util.cache;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;

import java.util.Arrays;

public class CachedChunkSectionAccess {
    private static final BlockState DEFAULT_STATE = Blocks.AIR.getDefaultState();

    private final ChunkProvider chunkProvider;

    private final long[] cachedCoords = new long[2];
    private final ChunkSection[][] cachedSectionArrays = new ChunkSection[2][];

    public CachedChunkSectionAccess(ChunkProvider provider) {
        this.chunkProvider = provider;
    }

    public BlockState getBlockState(int x, int y, int z) {
        if (y < 0 || y >= 256) {
            return DEFAULT_STATE;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        long pos = ChunkPos.toLong(chunkX, chunkZ);

        ChunkSection[] sections;

        if (this.cachedCoords[0] == pos) {
            sections = this.cachedSectionArrays[0];
        } else if (this.cachedCoords[1] == pos) {
            sections = this.cachedSectionArrays[1];
        } else {
            sections = this.getSectionArray(chunkX, chunkZ);

            this.cachedSectionArrays[1] = this.cachedSectionArrays[0];
            this.cachedSectionArrays[0] = sections;

            this.cachedCoords[1] = this.cachedCoords[0];
            this.cachedCoords[0] = pos;
        }

        if (sections == null) {
            return null;
        }

        ChunkSection section = sections[y >> 4];

        if (section == null) {
            return DEFAULT_STATE;
        }

        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    private ChunkSection[] getSectionArray(int chunkX, int chunkZ) {
        Chunk chunk = (Chunk) this.chunkProvider.getChunk(chunkX, chunkZ);

        return chunk != null ? chunk.getSectionArray() : null;
    }

    public void reset() {
        Arrays.fill(this.cachedCoords, Long.MIN_VALUE);
        Arrays.fill(this.cachedSectionArrays, null);
    }
}
