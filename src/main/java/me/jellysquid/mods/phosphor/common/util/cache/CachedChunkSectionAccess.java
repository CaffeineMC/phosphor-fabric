package me.jellysquid.mods.phosphor.common.util.cache;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;

public class CachedChunkSectionAccess extends AbstractCachedAccess {
    private static final ChunkSection[] EMPTY_SECTION = new ChunkSection[16];

    private final ChunkProvider chunkProvider;

    @SuppressWarnings("unchecked")
    private CachedEntry<ChunkSection[]>[] cached = new CachedEntry[]{new CachedEntry<>(), new CachedEntry<>()};

    public CachedChunkSectionAccess(ChunkProvider provider) {
        this.chunkProvider = provider;
    }

    public BlockState getBlockState(int x, int y, int z) {
        ChunkSection section = this.getCachedChunkSection(x, y, z);

        if (section == null) {
            return null;
        }

        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    // The manual if-branches here are significantly faster than starting a for-loop and iterating over
    // the array of cached entries.
    private ChunkSection getCachedChunkSection(int blockX, int blockY, int blockZ) {
        if (blockY < 0 || blockY >= 256) {
            return null;
        }

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        CachedEntry<ChunkSection[]>[] cache = this.cached;

        for (CachedEntry<ChunkSection[]> entry : cache) {
            if (entry.x == chunkX && entry.z == chunkZ) {
                return entry.obj[blockY >> 4];
            }
        }

        ChunkSection[] sections = this.getChunk(chunkX, chunkZ);

        CachedEntry<ChunkSection[]> last = cache[1];
        last.x = chunkX;
        last.z = chunkZ;
        last.obj = sections;

        cache[1] = cache[0];
        cache[0] = last;

        return sections[blockY >> 4];
    }

    private ChunkSection[] getChunk(int chunkX, int chunkZ) {
        Chunk chunk = (Chunk) this.chunkProvider.getChunk(chunkX, chunkZ);

        if (chunk != null) {
            return chunk.getSectionArray();
        }

        return EMPTY_SECTION;
    }

    public void cleanup() {
        for (CachedEntry<ChunkSection[]> entry : this.cached) {
            entry.reset();
        }
    }
}
