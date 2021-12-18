package net.caffeinemc.phosphor.mixin.chunk;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mixin(WorldChunk.class)
public abstract class MixinWorldChunk extends Chunk {
    private MixinWorldChunk(ChunkPos pos, UpgradeData upgradeData, HeightLimitView heightLimitView, Registry<Biome> biome, long inhabitedTime, ChunkSection[] sectionArrayInitializer, BlendingData blendingData) {
        super(pos, upgradeData, heightLimitView, biome, inhabitedTime, sectionArrayInitializer, blendingData);
    }

    /**
     * This implementation avoids iterating over empty chunk sections and uses direct access to read out block states
     * instead. Instead of allocating a BlockPos for every block in the chunk, they're now only allocated once we find
     * a light source.
     *
     * @reason Use optimized implementation
     * @author JellySquid
     */
    @Overwrite
    public Stream<BlockPos> getLightSourcesStream() {
        List<BlockPos> list = new ArrayList<>();

        int startX = this.pos.getStartX();
        int startZ = this.pos.getStartZ();

        ChunkSection[] chunkSections = this.sectionArray;

        for (ChunkSection section : chunkSections) {
            if (section == null || section.isEmpty()) {
                continue;
            }

            int startY = section.getYOffset();

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);

                        if (state.getLuminance() != 0) {
                            list.add(new BlockPos(startX + x, startY + y, startZ + z));
                        }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            return Stream.empty();
        }

        return list.stream();
    }
}
