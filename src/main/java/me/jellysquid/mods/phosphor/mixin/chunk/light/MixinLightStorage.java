package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import me.jellysquid.mods.phosphor.common.chunk.light.LightInitializer;
import me.jellysquid.mods.phosphor.common.chunk.light.LightProviderUpdateTracker;
import me.jellysquid.mods.phosphor.common.chunk.light.SharedLightStorageAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.StampedLock;

@SuppressWarnings("OverwriteModifiers")
@Mixin(LightStorage.class)
public abstract class MixinLightStorage<M extends ChunkToNibbleArrayMap<M>> implements SharedLightStorageAccess<M> {
    @Shadow
    @Final
    protected M lightArrays;

    @Mutable
    @Shadow
    @Final
    protected LongSet field_15802;

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

    @Shadow
    protected abstract ChunkNibbleArray getLightArray(long sectionPos, boolean cached);

    @Shadow
    @Final
    private ChunkProvider chunkProvider;

    @Shadow
    @Final
    private LightType lightType;

    @Shadow
    @Final
    private LongSet field_25621;

    private final StampedLock uncachedLightArraysLock = new StampedLock();

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
        if (provider instanceof LightProviderUpdateTracker) {
            ((LightProviderUpdateTracker) provider).cancelUpdatesForChunk(pos);

            ci.cancel();
        }
    }

    private final LongSet propagating = new LongOpenHashSet();

    /**
     * @reason Avoid integer boxing, reduce map lookups and iteration as much as possible
     * @author JellySquid
     */
    @Overwrite
    public void updateLightArrays(ChunkLightProvider<M, ?> chunkLightProvider, boolean doSkylight, boolean skipEdgeLightPropagation) {
        if (!this.hasLightUpdates() && this.lightArraysToAdd.isEmpty()) {
            return;
        }

        LongSet propagating = this.propagating;
        propagating.clear();

        LongIterator it = this.lightArraysToRemove.iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            this.removeChunkData(chunkLightProvider, pos);

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

        ObjectIterator<Long2ObjectMap.Entry<ChunkNibbleArray>> addQueue = Long2ObjectMaps.fastIterator(this.lightArraysToAdd);

        while (addQueue.hasNext()) {
            Long2ObjectMap.Entry<ChunkNibbleArray> entry = addQueue.next();
            long pos = entry.getLongKey();

            if (this.hasLight(pos)) {
                ChunkNibbleArray array = entry.getValue();

                if (this.lightArrays.get(pos) != array) {
                    this.removeChunkData(chunkLightProvider, pos);

                    this.lightArrays.put(pos, array);
                    this.field_15802.add(pos);
                }

                // If edge light propagation will occur, we need to add the set of removed items to an intermediary set
                // so the propagation code will not update touching faces of these sections
                if (!skipEdgeLightPropagation) {
                    propagating.add(pos);
                }

                // Early remove the entries from the queue so we don't have to later iterate and check hasLight again
                addQueue.remove();
            }
        }

        this.lightArrays.clearCache();

        if (!skipEdgeLightPropagation) {
            it = propagating.iterator();

            while (it.hasNext()) {
                method_29967(chunkLightProvider, it.nextLong());
            }
        } else {
            it = this.field_25621.iterator();

            while (it.hasNext()) {
                method_29967(chunkLightProvider, it.nextLong());
            }
        }

        this.field_25621.clear();

        // Vanilla would normally iterate back over the map of light arrays to remove those we worked on, but
        // that is unneeded now because we removed them earlier.
    }

    /**
     * @reason Avoid integer boxing, reduce map lookups and iteration as much as possible
     * @author JellySquid
     */
    @Overwrite
    private void method_29967(ChunkLightProvider<M, ?> chunkLightProvider, long pos) {
        if (this.hasLight(pos)) {
            int x = ChunkSectionPos.getWorldCoord(ChunkSectionPos.getX(pos));
            int y = ChunkSectionPos.getWorldCoord(ChunkSectionPos.getY(pos));
            int z = ChunkSectionPos.getWorldCoord(ChunkSectionPos.getZ(pos));

            for (Direction dir : DIRECTIONS) {
                long adjPos = ChunkSectionPos.offset(pos, dir);

                // Avoid updating initializing chunks unnecessarily
                if (propagating.contains(adjPos)) {
                    continue;
                }

                // If there is no light data for this section yet, skip it
                if (!this.hasLight(adjPos)) {
                    continue;
                }

                for (int u1 = 0; u1 < 16; ++u1) {
                    for (int u2 = 0; u2 < 16; ++u2) {
                        long a;
                        long b;

                        switch (dir) {
                            case DOWN:
                                a = BlockPos.asLong(x + u2, y, z + u1);
                                b = BlockPos.asLong(x + u2, y - 1, z + u1);
                                break;
                            case UP:
                                a = BlockPos.asLong(x + u2, y + 15, z + u1);
                                b = BlockPos.asLong(x + u2, y + 16, z + u1);
                                break;
                            case NORTH:
                                a = BlockPos.asLong(x + u1, y + u2, z);
                                b = BlockPos.asLong(x + u1, y + u2, z - 1);
                                break;
                            case SOUTH:
                                a = BlockPos.asLong(x + u1, y + u2, z + 15);
                                b = BlockPos.asLong(x + u1, y + u2, z + 16);
                                break;
                            case WEST:
                                a = BlockPos.asLong(x, y + u1, z + u2);
                                b = BlockPos.asLong(x - 1, y + u1, z + u2);
                                break;
                            case EAST:
                                a = BlockPos.asLong(x + 15, y + u1, z + u2);
                                b = BlockPos.asLong(x + 16, y + u1, z + u2);
                                break;
                            default:
                                continue;
                        }

                        ((LightInitializer) chunkLightProvider).spreadLightInto(a, b);
                    }
                }
            }
        }
    }

    /**
     * @reason
     * @author JellySquid
     */
    @Overwrite
    public void notifyChunkProvider() {
        if (!this.field_15802.isEmpty()) {
            // This could result in changes being flushed to various arrays, so write lock.
            long stamp = this.uncachedLightArraysLock.writeLock();

            try {
                // This only performs a shallow copy compared to before
                M map = this.lightArrays.copy();
                map.disableCache();

                this.uncachedLightArrays = map;
            } finally {
                this.uncachedLightArraysLock.unlockWrite(stamp);
            }

            this.field_15802.clear();
        }

        if (!this.dirtySections.isEmpty()) {
            LongIterator it = this.dirtySections.iterator();

            while(it.hasNext()) {
                long pos = it.nextLong();

                this.chunkProvider.onLightUpdate(this.lightType, ChunkSectionPos.from(pos));
            }

            this.dirtySections.clear();
        }
    }

    @Override
    public M getStorage() {
        return this.uncachedLightArrays;
    }

    @Override
    public StampedLock getStorageLock() {
        return this.uncachedLightArraysLock;
    }
}
