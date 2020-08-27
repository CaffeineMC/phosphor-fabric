package me.jellysquid.mods.phosphor.mixin.chunk;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Mixin(WorldChunk.class)
public abstract class MixinWorldChunk {
    @Shadow
    @Final
    private ChunkPos pos;

    @Shadow
    @Final
    private ChunkSection[] sections;

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
        final int startX = this.pos.getStartX();
        final int startZ = this.pos.getStartZ();

        return Arrays.stream(this.sections).flatMap(section -> {
            final int startY = section.getYOffset();
            return IntStream.range(0, 16).boxed().flatMap(x -> IntStream.range(0, 16).boxed().flatMap(y -> IntStream.range(0, 16).boxed().map(z -> section.getBlockState(x, y, z).getLuminance() == 0 ? null : new BlockPos(startX + x, startY + y, startZ + z))));
        }).filter(Objects::nonNull);
    }
}
