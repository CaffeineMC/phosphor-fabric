package me.jellysquid.mods.phosphor.common.util.cache;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;

import java.util.Arrays;

public class LightEngineBlockAccess {
    private static final BlockState DEFAULT_STATE = Blocks.AIR.getDefaultState();

    private final ChunkProvider chunkProvider;

    private final long[] cachedCoords = new long[2];
    private final ChunkSection[][] cachedSectionArrays = new ChunkSection[2][];

    public LightEngineBlockAccess(ChunkProvider provider) {
        this.chunkProvider = provider;
    }

    public BlockState getBlockState(int x, int y, int z) {
        if (y < 0 || y >= 256) {
            return DEFAULT_STATE;
        }

        ChunkSection[] sections = this.getCachedSection(x >> 4, z >> 4);

        if (sections != null) {
            ChunkSection section = sections[y >> 4];

            if (section == null) {
                return DEFAULT_STATE;
            }

            return section.getBlockState(x & 15, y & 15, z & 15);
        }

        return null;
    }

    private ChunkSection[] getCachedSection(int x, int z) {
        long[] cachedCoords = this.cachedCoords;

        long coord = ChunkPos.toLong(x, z);

        for (int i = 0; i < cachedCoords.length; i++) {
            if (cachedCoords[i] == coord) {
                return this.cachedSectionArrays[i];
            }
        }

        return this.retrieveChunkSection(coord, x, z);
    }

    private ChunkSection[] retrieveChunkSection(long coord, int x, int z) {
        ChunkSection[] sections = this.retrieveChunkSections(x, z);
        this.addToCache(coord, sections);

        return sections;
    }

    private ChunkSection[] retrieveChunkSections(int chunkX, int chunkZ) {
        Chunk chunk = (Chunk) this.chunkProvider.getChunk(chunkX, chunkZ);

        if (chunk == null) {
            return null;
        }

        return chunk.getSectionArray();
    }

    private void addToCache(long coord, ChunkSection[] sections) {
        ChunkSection[][] cachedSections = this.cachedSectionArrays;
        cachedSections[1] = cachedSections[0];
        cachedSections[0] = sections;

        long[] cachedCoords = this.cachedCoords;
        cachedCoords[1] = cachedCoords[0];
        cachedCoords[0] = coord;
    }

    public void reset() {
        Arrays.fill(this.cachedCoords, Long.MIN_VALUE);
        Arrays.fill(this.cachedSectionArrays, null);
    }
}
