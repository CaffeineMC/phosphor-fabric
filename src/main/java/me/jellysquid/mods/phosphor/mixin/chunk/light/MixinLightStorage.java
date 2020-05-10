package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import me.jellysquid.mods.phosphor.common.chunk.light.ChunkLightProviderExtended;
import me.jellysquid.mods.phosphor.common.chunk.light.LightStorageAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;

@Mixin(LightStorage.class)
public abstract class MixinLightStorage<M extends ChunkToNibbleArrayMap<M>> implements LightStorageAccess<M> {
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

    @SuppressWarnings("unused")
    @Shadow
    protected volatile boolean hasLightUpdates;

    @Shadow
    protected volatile M uncachedLightArrays;

    @Shadow
    protected abstract ChunkNibbleArray createLightArray(long pos);

    @Shadow
    @Final
    protected Long2ObjectMap<ChunkNibbleArray> lightArraysToAdd;

    @Shadow
    protected abstract boolean hasLightUpdates();

    @Shadow
    @Final
    private LongSet field_19342;

    @Shadow
    protected abstract void onChunkRemoved(long l);

    @Shadow
    public abstract boolean hasLight(long sectionPos);

    @Shadow
    @Final
    private static Direction[] DIRECTIONS;

    @Shadow
    protected abstract void removeChunkData(ChunkLightProvider<?, ?> storage, long blockChunkPos);

    /**
     * Replaces the two set of calls to unpack the XYZ coordinates from the input to just one, storing the result as local
     * variables.
     *
     * @reason Use faster implementation
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
     * @reason Use faster implementation
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
     * @reason Use faster implementation
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

    /**
     * @reason Drastically improve efficiency by making removals O(n) instead of O(16*16*16)
     * @author JellySquid
     */
    @Inject(method = "removeChunkData", at = @At("HEAD"), cancellable = true)
    protected void removeChunkData(ChunkLightProvider<?, ?> provider, long pos, CallbackInfo ci) {
        if (provider instanceof ChunkLightProviderExtended) {
            ((ChunkLightProviderExtended) provider).cancelUpdatesForChunk(pos);

            ci.cancel();
        }
    }

    /**
     * @reason Avoid integer boxing
     * @author JellySquid
     */
    @Overwrite
    public void updateLightArrays(ChunkLightProvider<M, ?> lightProvider, boolean doSkylight, boolean skipEdgeLightPropagation) {
        if (!this.hasLightUpdates() && this.lightArraysToAdd.isEmpty()) {
            return;
        }

        LongIterator it = this.lightArraysToRemove.iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            this.removeChunkData(lightProvider, pos);

            ChunkNibbleArray pending = this.lightArraysToAdd.remove(pos);
            ChunkNibbleArray existing = this.lightArrays.removeChunk(pos);

            if (this.field_19342.contains(ChunkSectionPos.withZeroZ(pos))) {
                if (pending != null) {
                    this.lightArraysToAdd.put(pos, pending);
                } else if (existing != null) {
                    this.lightArraysToAdd.put(pos, existing);
                }
            }
        }

        this.lightArrays.clearCache();
        it = this.lightArraysToRemove.iterator();

        while (it.hasNext()) {
            this.onChunkRemoved(it.nextLong());
        }

        this.lightArraysToRemove.clear();
        this.hasLightUpdates = false;

        ObjectIterator<Long2ObjectMap.Entry<ChunkNibbleArray>> addQueue = getFastIterator(this.lightArraysToAdd);

        while (addQueue.hasNext()) {
            Long2ObjectMap.Entry<ChunkNibbleArray> entry = addQueue.next();
            long pos = entry.getLongKey();

            if (this.hasLight(pos)) {
                ChunkNibbleArray chunkNibbleArray3 = entry.getValue();

                if (this.lightArrays.get(pos) != chunkNibbleArray3) {
                    this.removeChunkData(lightProvider, pos);

                    this.lightArrays.put(pos, chunkNibbleArray3);
                    this.field_15802.add(pos);
                }
            }
        }

        this.lightArrays.clearCache();

        if (!skipEdgeLightPropagation) {
            it = this.lightArraysToAdd.keySet().iterator();

            while (it.hasNext()) {
                long pos = it.nextLong();

                if (!this.hasLight(pos)) {
                    continue;
                }

                int x = ChunkSectionPos.getWorldCoord(ChunkSectionPos.getX(pos));
                int y = ChunkSectionPos.getWorldCoord(ChunkSectionPos.getY(pos));
                int z = ChunkSectionPos.getWorldCoord(ChunkSectionPos.getZ(pos));

                for (Direction dir : DIRECTIONS) {
                    long adjPos = ChunkSectionPos.offset(pos, dir);

                    if (this.lightArraysToAdd.containsKey(adjPos) || !this.hasLight(adjPos)) {
                        continue;
                    }

                    for (int q = 0; q < 16; ++q) {
                        for (int r = 0; r < 16; ++r) {
                            long a;
                            long b;

                            switch (dir) {
                                case DOWN:
                                    a = BlockPos.asLong(x + r, y, z + q);
                                    b = BlockPos.asLong(x + r, y - 1, z + q);
                                    break;
                                case UP:
                                    a = BlockPos.asLong(x + r, y + 16 - 1, z + q);
                                    b = BlockPos.asLong(x + r, y + 16, z + q);
                                    break;
                                case NORTH:
                                    a = BlockPos.asLong(x + q, y + r, z);
                                    b = BlockPos.asLong(x + q, y + r, z - 1);
                                    break;
                                case SOUTH:
                                    a = BlockPos.asLong(x + q, y + r, z + 16 - 1);
                                    b = BlockPos.asLong(x + q, y + r, z + 16);
                                    break;
                                case WEST:
                                    a = BlockPos.asLong(x, y + q, z + r);
                                    b = BlockPos.asLong(x - 1, y + q, z + r);
                                    break;
                                case EAST:
                                    a = BlockPos.asLong(x + 16 - 1, y + q, z + r);
                                    b = BlockPos.asLong(x + 16, y + q, z + r);
                                    break;
                                default:
                                    continue;
                            }

                            ((ChunkLightProviderExtended) lightProvider).spreadLightInto(a, b);
                        }
                    }
                }
            }
        }

        addQueue = getFastIterator(this.lightArraysToAdd);

        while (addQueue.hasNext()) {
            Long2ObjectMap.Entry<ChunkNibbleArray> entry2 = addQueue.next();
            long pos = entry2.getLongKey();

            if (this.hasLight(pos)) {
                addQueue.remove();
            }
        }
    }

    /**
     * Returns a fast iterator over the entries in {@param map}. If the collection type is not a fast type, then the
     * fallback iterator is used. The fast iterator does not allocate a new object for each entry returned by the
     * iterator, meaning that the result of {@link Iterator#next()} will always be the same object.
     */
    private static <T> ObjectIterator<Long2ObjectMap.Entry<T>> getFastIterator(Long2ObjectMap<T> map) {
        if (map instanceof Long2ObjectOpenHashMap) {
            return ((Long2ObjectOpenHashMap<T>) map).long2ObjectEntrySet().fastIterator();
        } else if (map instanceof Long2ObjectLinkedOpenHashMap) {
            return ((Long2ObjectLinkedOpenHashMap<T>) map).long2ObjectEntrySet().fastIterator();
        } else {
            return map.long2ObjectEntrySet().iterator();
        }
    }

    @Override
    public M getStorageUncached() {
        return this.uncachedLightArrays;
    }
}
