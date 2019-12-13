package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.LongSet;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedLightStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.*;

@Mixin(LightStorage.class)
public abstract class MixinLightStorage<M extends ChunkToNibbleArrayMap<M>> implements ExtendedLightStorage<M> {
    @Shadow
    @Final
    protected M lightArrays;

    @Mutable
    @Shadow
    @Final
    protected LongSet field_15802;

    @Shadow
    protected abstract ChunkNibbleArray getLightArray(long chunkPos, boolean cached);

    @Mutable
    @Shadow
    @Final
    protected LongSet dirtySections;

    @Shadow
    protected abstract boolean hasLight(long chunkPos);

    @Shadow
    protected abstract int getLevel(long id);

    @Mutable
    @Shadow
    @Final
    protected LongSet nonEmptySections;

    @Mutable
    @Shadow
    @Final
    protected LongSet field_15804;

    @Mutable
    @Shadow
    @Final
    protected LongSet field_15797;

    @Mutable
    @Shadow
    @Final
    private LongSet lightArraysToRemove;

    @Shadow
    protected abstract void onLightArrayCreated(long blockPos);

    @Shadow
    public abstract ChunkNibbleArray getLightArray(long chunkPos);

    @SuppressWarnings("unused")
    @Shadow
    protected volatile boolean hasLightUpdates;

    @Shadow
    protected volatile M uncachedLightArrays;

    @Shadow
    protected abstract ChunkNibbleArray createLightArray(long pos);

    /**
     * Replaces the two set of calls to unpack the XYZ coordinates from the input to just one, storing the result as local
     * variables.
     *
     * @author JellySquid
     */
    @Overwrite
    public int get(long blockPos) {
        int x = BlockPos.unpackLongX(blockPos);
        int y = BlockPos.unpackLongY(blockPos);
        int z = BlockPos.unpackLongZ(blockPos);

        long chunk = ChunkSectionPos.asLong(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(y), ChunkSectionPos.getSectionCoord(z));

        ChunkNibbleArray array = this.getLightArray(chunk, true);

        return array.get(ChunkSectionPos.getLocalCoord(x), ChunkSectionPos.getLocalCoord(y), ChunkSectionPos.getLocalCoord(z));
    }

    /**
     * An extremely important optimization is made here in regards to adding items to the pending notification set. The
     * original implementation attempts to add the coordinate of every chunk which contains a neighboring block position
     * even though a huge number of loop iterations will simply map to block positions within the same updating chunk.
     * <p>
     * Our implementation here avoids this by pre-calculating the min/max chunk coordinates so we can iterate over only
     * the relevant chunk positions once. This reduces what would always be 27 iterations to just 1-8 iterations.
     *
     * @author JellySquid
     */
    @Overwrite
    public void set(long blockPos, int value) {
        int x = BlockPos.unpackLongX(blockPos);
        int y = BlockPos.unpackLongY(blockPos);
        int z = BlockPos.unpackLongZ(blockPos);

        long chunkPos = ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4);

        if (this.field_15802.add(chunkPos)) {
            this.lightArrays.replaceWithCopy(chunkPos);
        }

        ChunkNibbleArray nibble = this.getLightArray(chunkPos, true);
        nibble.set(x & 15, y & 15, z & 15, value);

        for (int z2 = (z - 1) >> 4; z2 <= (z + 1) >> 4; ++z2) {
            for (int x2 = (x - 1) >> 4; x2 <= (x + 1) >> 4; ++x2) {
                for (int y2 = (y - 1) >> 4; y2 <= (y + 1) >> 4; ++y2) {
                    this.dirtySections.add(ChunkSectionPos.asLong(x2, y2, z2));
                }
            }
        }
    }

    /**
     * Combines the contains/remove call to the queued removals set into a single remove call. See {@link MixinLightStorage#set(long, int)}
     * for additional information.
     *
     * @author JellySquid
     */
    @Overwrite
    public void setLevel(long id, int level) {
        int prevLevel = this.getLevel(id);

        if (prevLevel != 0 && level == 0) {
            this.nonEmptySections.add(id);
            this.field_15804.remove(id);
        }

        if (prevLevel == 0 && level != 0) {
            this.nonEmptySections.remove(id);
            this.field_15797.remove(id);
        }

        if (prevLevel >= 2 && level != 2) {
            if (!this.lightArraysToRemove.remove(id)) {
                this.lightArrays.put(id, this.createLightArray(id));

                this.field_15802.add(id);
                this.onLightArrayCreated(id);

                int x = BlockPos.unpackLongX(id);
                int y = BlockPos.unpackLongY(id);
                int z = BlockPos.unpackLongZ(id);

                for (int z2 = (z - 1) >> 4; z2 <= (z + 1) >> 4; ++z2) {
                    for (int x2 = (x - 1) >> 4; x2 <= (x + 1) >> 4; ++x2) {
                        for (int y2 = (y - 1) >> 4; y2 <= (y + 1) >> 4; ++y2) {
                            this.dirtySections.add(ChunkSectionPos.asLong(x2, y2, z2));
                        }
                    }
                }
            }
        }

        if (prevLevel != 2 && level >= 2) {
            this.lightArraysToRemove.add(id);
        }

        this.hasLightUpdates = !this.lightArraysToRemove.isEmpty();
    }

    @Override
    public boolean bridge$hasChunk(long chunkPos) {
        return this.hasLight(chunkPos);
    }

    @Override
    public ChunkNibbleArray bridge$getDataForChunk(M data, long chunkPos) {
        return data.get(chunkPos);
    }

    @Override
    public M bridge$getStorageUncached() {
        return this.uncachedLightArrays;
    }
}
