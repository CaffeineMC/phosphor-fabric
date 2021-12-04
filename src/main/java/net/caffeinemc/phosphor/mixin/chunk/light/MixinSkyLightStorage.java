package net.caffeinemc.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.phosphor.common.chunk.light.IReadonly;
import net.caffeinemc.phosphor.common.chunk.light.LevelPropagatorAccess;
import net.caffeinemc.phosphor.common.chunk.light.SkyLightStorageAccess;
import net.caffeinemc.phosphor.common.chunk.light.SkyLightStorageDataAccess;
import net.caffeinemc.phosphor.common.util.chunk.light.EmptyChunkNibbleArray;
import net.caffeinemc.phosphor.common.util.chunk.light.SkyLightChunkNibbleArray;
import net.caffeinemc.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

@Mixin(SkyLightStorage.class)
public abstract class MixinSkyLightStorage extends MixinLightStorage implements SkyLightStorageAccess {
    /**
     * An optimized implementation which avoids constantly unpacking and repacking integer coordinates.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Overwrite
    public int getLight(long pos) {
        int posX = BlockPos.unpackLongX(pos);
        int posYOrig = BlockPos.unpackLongY(pos);
        int posZ = BlockPos.unpackLongZ(pos);

        int chunkX = ChunkSectionPos.getSectionCoord(posX);
        int chunkYOrig = ChunkSectionPos.getSectionCoord(posYOrig);
        int chunkZ = ChunkSectionPos.getSectionCoord(posZ);

        long chunkOrig = ChunkSectionPos.asLong(chunkX, chunkYOrig, chunkZ);

        StampedLock lock = this.uncachedLightArraysLock;
        long stamp;

        ChunkNibbleArray array;

        optimisticRead:
        while (true) {
            stamp = lock.tryOptimisticRead();

            int posY = posYOrig;
            int chunkY = chunkYOrig;
            long chunk = chunkOrig;

            ChunkToNibbleArrayMap<?> data = this.uncachedStorage;
            SkyLightStorageDataAccess sdata = (SkyLightStorageDataAccess) data;

            int height = sdata.getHeight(ChunkSectionPos.withZeroY(chunk));

            if (height == sdata.getDefaultHeight() || chunkY >= height) {
                if (lock.validate(stamp)) {
                    return 15;
                } else {
                    continue;
                }
            }

            array = data.get(chunk);

            while (array == null) {
                ++chunkY;

                if (chunkY >= height) {
                    if (lock.validate(stamp)) {
                        return 15;
                    } else {
                        continue optimisticRead;
                    }
                }

                chunk = ChunkSectionPosHelper.updateYLong(chunk, chunkY);
                array = data.get(chunk);

                posY = chunkY << 4;
            }

            if (lock.validate(stamp)) {
                return array.get(
                    ChunkSectionPos.getLocalCoord(posX),
                    ChunkSectionPos.getLocalCoord(posY),
                    ChunkSectionPos.getLocalCoord(posZ)
                );
            }
        }
    }

    @Shadow
    protected abstract boolean isSectionEnabled(long sectionPos);

    @Override
    public int getLightWithoutLightmap(final long blockPos) {
        final long sectionPos = ChunkSectionPos.fromBlockPos(blockPos);
        final ChunkNibbleArray lightmap = this.getLightmapAbove(sectionPos);

        if (lightmap == null) {
            return this.isSectionEnabled(sectionPos) ? 15 : 0;
        }

        return lightmap.get(ChunkSectionPos.getLocalCoord(BlockPos.unpackLongX(blockPos)), 0, ChunkSectionPos.getLocalCoord(BlockPos.unpackLongZ(blockPos)));
    }

    @Redirect(
        method = "createSection(J)Lnet/minecraft/world/chunk/ChunkNibbleArray;",
        at = @At(
            value = "NEW",
            target = "()Lnet/minecraft/world/chunk/ChunkNibbleArray;"
        )
    )
    private ChunkNibbleArray initializeLightmap(final long pos) {
        final ChunkNibbleArray ret = new ChunkNibbleArray();

        if (this.isSectionEnabled(pos)) {
            Arrays.fill(ret.asByteArray(), (byte) -1);
        }

        return ret;
    }

    @Override
    protected void beforeChunkEnabled(final long chunkPos) {
        // Initialize height data

        int minHeight = Integer.MAX_VALUE;
        int height = Integer.MIN_VALUE;

        for (final IntIterator it = this.getTrackedSections(chunkPos); it.hasNext(); ) {
            final int y = it.nextInt();
            final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, y);

            if (y < minHeight && (this.nonOptimizableSections.contains(sectionPos) || this.getLightSection(sectionPos, true) != null)) {
                minHeight = y;
            }

            if (y > height && this.readySections.contains(sectionPos)) {
                height = y;
            }
        }

        this.updateMinHeight(minHeight);

        if (height != Integer.MIN_VALUE) {
            this.setHeight(chunkPos, height);
        }

        // Remove lightmaps above the topmost non-empty section. These should only be trivial Vanilla lightmaps and are hence safe to remove
        // Any non-trivial lightmap would be a glitch, anyway
        // Removing these lightmaps is slightly more efficient and ensures triviality above the topmost non-empty section for non-lighted chunks

        for (final IntIterator it = this.getTrackedSections(chunkPos); it.hasNext(); ) {
            final int y = it.nextInt();

            if (y <= height) {
                continue;
            }

            final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, y);

            if (this.storage.removeChunk(sectionPos) != null) {
                this.untrackSection(chunkPos, y);
                this.dirtySections.add(sectionPos);
            }
        }

        this.storage.clearCache();

        // Initialize Vanilla lightmap complexities

        for (final IntIterator it = this.getTrackedSections(chunkPos); it.hasNext(); ) {
            final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, it.nextInt());
            final ChunkNibbleArray lightmap = this.getLightSection(sectionPos, true);

            if (lightmap != null) {
                this.initializeVanillaLightmapComplexity(sectionPos, lightmap);
            }
        }
    }

    @Override
    protected void afterChunkDisabled(final long chunkPos, final IntIterable removedLightmaps) {
        for (IntIterator it = removedLightmaps.iterator(); it.hasNext(); ) {
            this.vanillaLightmapComplexities.remove(ChunkSectionPosHelper.updateYLong(chunkPos, it.nextInt()));
        }

        ((SkyLightStorageDataAccess) this.storage).setHeight(chunkPos, this.getMinHeight());
        this.scheduledHeightChecks.remove(chunkPos);
        this.scheduledHeightIncreases.remove(chunkPos);
    }

    @Unique
    private final LongSet initSkylightChunks = new LongOpenHashSet();

    @Shadow
    @Final
    private LongSet enabledColumns;

    /**
     * @author PhiPro
     * @reason Re-implement completely.
     * This method now schedules initial lighting when enabling source light for a chunk that already has light updates enabled.
     */
    @Override
    @Overwrite
    public void setColumnEnabled(final long chunkPos, final boolean enabled) {
        if (enabled) {
            if (this.enabledChunks.contains(chunkPos) && !this.enabledColumns.contains(chunkPos)) {
                this.initSkylightChunks.add(chunkPos);
                this.markForUpdates();
            } else {
                this.enabledColumns.add(chunkPos);
            }
        } else {
            this.enabledColumns.remove(chunkPos);
            this.initSkylightChunks.remove(chunkPos);
        }
    }

    @Unique
    private static void spreadSourceSkylight(final LevelPropagatorAccess lightProvider, final long src, final Direction dir) {
        lightProvider.invokePropagateLevel(src, BlockPos.offset(src, dir), 0, true);
    }

    @Unique
    private static void spreadZeroSkylight(final LevelPropagatorAccess lightProvider, final long src, final Direction dir, final int prevLight) {
        if (prevLight != 0) {
            lightProvider.invokePropagateLevel(src, BlockPos.offset(src, dir), 15 - prevLight, false);
        }
    }

    @Unique
    private static void pullSkylight(final LevelPropagatorAccess lightProvider, final long dst, final Direction dir) {
        lightProvider.propagateLevel(BlockPos.offset(dst, dir), dst, true);
    }

    @Override
    protected void runCleanups(final ChunkLightProvider<?, ?> lightProvider) {
        super.runCleanups(lightProvider);

        if (!this.hasUpdates) {
            return;
        }

        this.updateRemovedLightmaps();

        if (lightProvider == null) {
            this.checkForUpdates();
        }
    }

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    public void updateLight(final ChunkLightProvider<?, ?> lightProvider, final boolean doSkylight, final boolean skipEdgeLightPropagation) {
        super.updateLight(lightProvider, doSkylight, skipEdgeLightPropagation);

        if (!doSkylight || !this.hasUpdates) {
            return;
        }

        this.updateHeights(lightProvider);
        this.lightChunks(lightProvider);

        this.hasUpdates = false;
    }

    @Unique
    private void updateHeights(final ChunkLightProvider<?, ?> lightProvider) {
        final LevelPropagatorAccess levelPropagator = (LevelPropagatorAccess) lightProvider;

        // Increase height for non-lighted chunks and pull in light from neighbors

        if (!this.scheduledHeightIncreases.isEmpty()) {
            for (final Long2IntMap.Entry entry : this.scheduledHeightIncreases.long2IntEntrySet()) {
                final long chunkPos = entry.getLongKey();
                int height = entry.getIntValue();
                final int oldHeight = this.getHeight(chunkPos) - 1;

                // Calculate actual height

                for (; height > oldHeight; --height) {
                    if (this.readySections.contains(ChunkSectionPosHelper.updateYLong(chunkPos, height))) {
                        break;
                    }
                }

                if (height == oldHeight) {
                    continue;
                }

                // Update height, enabling light updates above

                this.setHeight(chunkPos, height);

                // Pull in light

                final int blockPosX = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackX(chunkPos));
                final int blockPosZ = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackZ(chunkPos));

                if (this.getLightSection(ChunkSectionPosHelper.updateYLong(chunkPos, oldHeight + 1), true) != null) {
                    final long blockPos = BlockPos.asLong(blockPosX, ChunkSectionPos.getBlockCoord(oldHeight + 1), blockPosZ);

                    for (int x = 0; x < 16; ++x) {
                        for (int z = 0; z < 16; ++z) {
                            pullSkylight(levelPropagator, BlockPos.add(blockPos, x, 0, z), Direction.DOWN);
                        }
                    }
                }

                for (final Direction dir : Direction.Type.HORIZONTAL) {
                    // Some neighbors may not be enabled if they do not yet contain any skylight at this boundary, so these can be skipped
                    if (!this.enabledChunks.contains(ChunkSectionPos.offset(chunkPos, dir))) {
                        continue;
                    }

                    final int ox = 15 * Math.max(dir.getOffsetX(), 0);
                    final int oz = 15 * Math.max(dir.getOffsetZ(), 0);

                    final int dx = Math.abs(dir.getOffsetZ());
                    final int dz = Math.abs(dir.getOffsetX());

                    for (int y = height; y > oldHeight; --y) {
                        if (this.getLightSection(ChunkSectionPosHelper.updateYLong(chunkPos, y), true) == null) {
                            continue;
                        }

                        final long blockPos = BlockPos.asLong(blockPosX, ChunkSectionPos.getBlockCoord(y), blockPosZ);

                        for (int t = 0; t < 16; ++t) {
                            for (int dy = 0; dy < 16; ++dy) {
                                pullSkylight(levelPropagator, BlockPos.add(blockPos, ox + t * dx, dy, oz + t * dz), dir);
                            }
                        }
                    }
                }
            }

            this.scheduledHeightIncreases.clear();
        }

        // Decrease heights

        if (!this.scheduledHeightChecks.isEmpty()) {
            for (final LongIterator it = this.scheduledHeightChecks.iterator(); it.hasNext(); ) {
                final long chunkPos = it.nextLong();

                int height = this.getHeight(chunkPos) - 1;

                if (!this.isAboveMinHeight(height)) {
                    continue;
                }

                if (this.enabledColumns.contains(chunkPos)) {
                    // Update height and remove pending light updates for the now skylight-optimizable sections

                    for (; this.isAboveMinHeight(height); --height) {
                        final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, height);

                        if (this.readySections.contains(sectionPos) || this.hasLightmap(sectionPos)) {
                            break;
                        }

                        if (this.getLightSection(sectionPos, true) != null) {
                            this.removeSection(lightProvider, sectionPos);
                        }
                    }

                    this.setHeight(chunkPos, height);
                } else {
                    // Set height to the topmost non-empty section and enforce trivial light above height

                    // First need to remove all pending light updates before changing any light value

                    int lightmapPosAbove = Integer.MIN_VALUE;
                    ChunkNibbleArray lightmapAbove = null;

                    for (; this.isAboveMinHeight(height); --height) {
                        final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, height);

                        if (this.readySections.contains(sectionPos)) {
                            break;
                        }

                        final ChunkNibbleArray lightmap = this.getLightSection(sectionPos, true);

                        if (lightmap != null) {
                            this.removeSection(lightProvider, sectionPos);

                            if (lightmapPosAbove == Integer.MIN_VALUE && !((IReadonly) lightmap).isReadonly()) {
                                lightmapPosAbove = height;
                                lightmapAbove = lightmap;
                            }
                        }
                    }

                    // Update height, disabling light updates above

                    this.setHeight(chunkPos, height);

                    if (lightmapPosAbove == Integer.MIN_VALUE) {
                        // Light is already zero above height, so there is nothing else to do
                        continue;
                    }

                    // Now light values can be changed
                    // Delete lightmaps so the sections inherit zero skylight and add trivial lightmaps for Vanilla compatibility
                    // Propagate changes to neighbors

                    final int blockPosX = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackX(chunkPos));
                    final int blockPosZ = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackZ(chunkPos));

                    final boolean hasSectionBelow = this.isAboveMinHeight(height);

                    for (int curY = lightmapPosAbove - 1; curY >= height; --curY) {
                        final long curSectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, curY);
                        final ChunkNibbleArray lightmap = this.getLightmap(curSectionPos);

                        // Search for next lightmap

                        if (curY > height && lightmap == null) {
                            continue;
                        }

                        // Set up a lightmap and adjust the complexity for the section below

                        if (curY == height && hasSectionBelow) {
                            // Light above will be zero after deleting the lightmaps
                            if (lightmap == null) {
                                this.getOrAddLightmap(curSectionPos);
                                this.setLightmapComplexity(curSectionPos, this.vanillaLightmapComplexities.get(ChunkSectionPosHelper.updateYLong(chunkPos, lightmapPosAbove)));
                            } else {
                                int amount = 0;

                                for (int z = 0; z < 16; ++z) {
                                    for (int x = 0; x < 16; ++x) {
                                        //noinspection ConstantConditions
                                        amount += getComplexityChange(lightmap.get(x, 15, z), lightmapAbove.get(x, 0, z), 0);
                                    }
                                }

                                this.changeLightmapComplexity(curSectionPos, amount);
                            }
                        }

                        // Process next batch of sections

                        // Update lightmaps

                        for (int y = lightmapPosAbove; y > curY; --y) {
                            final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, y);

                            // Delete lightmap

                            if (this.getLightSection(sectionPos, true) == null) {
                                continue;
                            }

                            if (this.removeLightmap(sectionPos)) {
                                this.vanillaLightmapComplexities.remove(sectionPos);
                            }

                            // Add trivial lightmap for Vanilla compatibility

                            if (this.nonOptimizableSections.contains(sectionPos)) {
                                this.storage.put(sectionPos, EMPTY_SKYLIGHT_MAP);
                            }
                        }

                        this.storage.clearCache();

                        // Propagate changes

                        if (curY == height && hasSectionBelow) {
                            final long blockPos = BlockPos.asLong(blockPosX, ChunkSectionPos.getBlockCoord(height + 1), blockPosZ);

                            for (int x = 0; x < 16; ++x) {
                                for (int z = 0; z < 16; ++z) {
                                    spreadZeroSkylight(levelPropagator, BlockPos.add(blockPos, x, 0, z), Direction.DOWN, lightmapAbove.get(x, 0, z));
                                }
                            }
                        }

                        for (final Direction dir : Direction.Type.HORIZONTAL) {
                            final int ox = 15 * Math.max(dir.getOffsetX(), 0);
                            final int oz = 15 * Math.max(dir.getOffsetZ(), 0);

                            final int dx = Math.abs(dir.getOffsetZ());
                            final int dz = Math.abs(dir.getOffsetX());

                            for (int y = lightmapPosAbove; y > curY; --y) {
                                final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, y);
                                final long neighborSectionPos = ChunkSectionPos.offset(sectionPos, dir);

                                if (!this.hasSection(neighborSectionPos)) {
                                    continue;
                                }

                                final long blockPos = BlockPos.asLong(blockPosX, ChunkSectionPos.getBlockCoord(y), blockPosZ);

                                for (int t = 0; t < 16; ++t) {
                                    for (int dy = 0; dy < 16; ++dy) {
                                        final int x = ox + t * dx;
                                        final int z = oz + t * dz;

                                        spreadZeroSkylight(levelPropagator, BlockPos.add(blockPos, x, dy, z), dir, lightmapAbove.get(x, y == lightmapPosAbove ? dy : 0, z));
                                    }
                                }
                            }
                        }

                        lightmapPosAbove = curY;
                        lightmapAbove = lightmap;
                    }
                }
            }

            this.scheduledHeightChecks.clear();
        }

        levelPropagator.checkForUpdates();
    }

    @Unique
    private void lightChunks(final ChunkLightProvider<?, ?> lightProvider) {
        if (this.initSkylightChunks.isEmpty()) {
            return;
        }

        final LevelPropagatorAccess levelPropagator = (LevelPropagatorAccess) lightProvider;

        for (final LongIterator cit = this.initSkylightChunks.iterator(); cit.hasNext(); ) {
            final long chunkPos = cit.nextLong();
            final int minY = this.getHeight(chunkPos) - 1;

            // Set up a lightmap and adjust the complexity for the section below

            final boolean hasSectionBelow = this.isAboveMinHeight(minY);

            if (hasSectionBelow) {
                final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, minY);
                final ChunkNibbleArray lightmap = this.getLightmap(sectionPos);

                // Light above the height is trivial
                if (lightmap == null) {
                    this.getOrAddLightmap(sectionPos);
                    this.setLightmapComplexity(sectionPos, 15 * 16 * 16);
                } else {
                    int amount = 0;

                    for (int z = 0; z < 16; ++z) {
                        for (int x = 0; x < 16; ++x) {
                            amount += getComplexityChange(lightmap.get(x, 15, z), 0, 15);
                        }
                    }

                    this.changeLightmapComplexity(sectionPos, amount);
                }
            }

            // Now light values can be changed (sections above height are skylight-optimizable)
            // Delete lightmaps so the sections inherit direct skylight and add trivial lightmaps for Vanilla compatibility

            for (final IntIterator it = this.getTrackedSections(chunkPos); it.hasNext(); ) {
                final int y = it.nextInt();

                if (y <= minY) {
                    continue;
                }

                final long sectionPos = ChunkSectionPosHelper.updateYLong(chunkPos, y);

                // All lightmaps above height are trivial, so no extra cleanup is required
                this.removeLightmap(sectionPos);

                if (this.nonOptimizableSections.contains(sectionPos)) {
                    this.storage.put(sectionPos, DIRECT_SKYLIGHT_MAP);
                }
            }

            this.storage.clearCache();

            this.enabledColumns.add(chunkPos);

            // Propagate skylight

            final int blockPosX = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackX(chunkPos));
            final int blockPosZ = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackZ(chunkPos));

            if (hasSectionBelow) {
                final long blockPos = BlockPos.asLong(blockPosX, ChunkSectionPos.getBlockCoord(minY + 1), blockPosZ);

                for (int x = 0; x < 16; ++x) {
                    for (int z = 0; z < 16; ++z) {
                        spreadSourceSkylight(levelPropagator, BlockPos.add(blockPos, x, 0, z), Direction.DOWN);
                    }
                }
            }

            for (final Direction dir : Direction.Type.HORIZONTAL) {
                final long neighborChunkPos = ChunkSectionPos.offset(chunkPos, dir);

                final int ox = 15 * Math.max(dir.getOffsetX(), 0);
                final int oz = 15 * Math.max(dir.getOffsetZ(), 0);

                final int dx = Math.abs(dir.getOffsetZ());
                final int dz = Math.abs(dir.getOffsetX());

                for (int y = this.getHeight(neighborChunkPos) - 1; y > minY; --y) {
                    if (!this.hasSection(ChunkSectionPosHelper.updateYLong(neighborChunkPos, y))) {
                        continue;
                    }

                    final long blockPos = BlockPos.asLong(blockPosX, ChunkSectionPos.getBlockCoord(y), blockPosZ);

                    for (int t = 0; t < 16; ++t) {
                        for (int dy = 0; dy < 16; ++dy) {
                            spreadSourceSkylight(levelPropagator, BlockPos.add(blockPos, ox + t * dx, dy, oz + t * dz), dir);
                        }
                    }
                }
            }
        }

        levelPropagator.checkForUpdates();
        this.initSkylightChunks.clear();
    }

    @Unique
    private void updateRemovedLightmaps() {
        while (!this.removedLightmaps.isEmpty()) {
            long sectionPos = this.removedLightmaps.iterator().nextLong();

            if (!this.enabledChunks.contains(ChunkSectionPos.withZeroY(sectionPos))) {
                continue;
            }

            long removedLightmapPosAbove = sectionPos;

            int y = ChunkSectionPos.unpackY(sectionPos);
            final int height = this.getHeight(ChunkSectionPos.withZeroY(sectionPos));

            if (height == this.getMinHeight()) {
                y = height;
            } else {
                for (; y < height; ++y) {
                    sectionPos = ChunkSectionPosHelper.updateYLong(sectionPos, y);

                    if (this.hasLightmap(sectionPos)) {
                        break;
                    }

                    if (this.removedLightmaps.contains(sectionPos)) {
                        removedLightmapPosAbove = sectionPos;
                    }
                }
            }

            final ChunkNibbleArray lightmapAbove;

            if (y >= height) {
                lightmapAbove = this.isSectionEnabled(sectionPos) ? DIRECT_SKYLIGHT_MAP : EMPTY_SKYLIGHT_MAP;
            } else {
                lightmapAbove = this.vanillaLightmapComplexities.get(sectionPos) == 0 ? EMPTY_SKYLIGHT_MAP : this.getLightSection(sectionPos, true);
            }

            this.updateVanillaLightmapsBelow(removedLightmapPosAbove, lightmapAbove);
        }
    }

    @Shadow
    private volatile boolean hasUpdates;

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    private void checkForUpdates() {
        this.hasUpdates = !this.initSkylightChunks.isEmpty() || !this.removedLightmaps.isEmpty() || !this.scheduledHeightIncreases.isEmpty() || !this.scheduledHeightChecks.isEmpty();
    }

    @Unique
    private void markForUpdates() {
        // Avoid volatile writes
        if (!this.hasUpdates) {
            this.hasUpdates = true;
        }
    }

    @Unique
    private static final ChunkNibbleArray DIRECT_SKYLIGHT_MAP = new SkyLightChunkNibbleArray(ArrayUtils.toPrimitive(new Byte[2048], (byte) -1));

    @Unique
    private static final ChunkNibbleArray EMPTY_SKYLIGHT_MAP = new EmptyChunkNibbleArray();

    @Unique
    private final Long2IntMap vanillaLightmapComplexities = new Long2IntOpenHashMap();
    @Unique
    private final LongSet removedLightmaps = new LongOpenHashSet();

    @Override
    public boolean hasSection(final long sectionPos) {
        return super.hasSection(sectionPos) && this.getLightSection(sectionPos, true) != null && !this.isAtOrAboveTopmostSection(sectionPos);
    }

    // Queued lightmaps are only added to the world via updateLightmaps()
    @Redirect(
        method = "createSection(J)Lnet/minecraft/world/chunk/ChunkNibbleArray;",
        slice = @Slice(
            from = @At(
                value = "FIELD",
                target = "Lnet/minecraft/world/chunk/light/SkyLightStorage;queuedSections:Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;",
                opcode = Opcodes.GETFIELD
            )
        ),
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;get(J)Ljava/lang/Object;",
            ordinal = 0,
            remap = false
        )
    )
    private Object cancelLightmapLookupFromQueue(final Long2ObjectMap<ChunkNibbleArray> lightmapArray, final long pos) {
        return null;
    }

    @Unique
    private static int getComplexityChange(final int val, final int oldNeighborVal, final int newNeighborVal) {
        return Math.abs(newNeighborVal - val) - Math.abs(oldNeighborVal - val);
    }

    @Override
    protected void beforeLightChange(final long blockPos, final int oldVal, final int newVal, final ChunkNibbleArray lightmap) {
        final long sectionPos = ChunkSectionPos.fromBlockPos(blockPos);

        if (ChunkSectionPos.getLocalCoord(BlockPos.unpackLongY(blockPos)) == 0) {
            this.vanillaLightmapComplexities.put(sectionPos, this.vanillaLightmapComplexities.get(sectionPos) + newVal - oldVal);

            final long sectionPosBelow = this.getSectionBelow(sectionPos);

            if (sectionPosBelow != Long.MAX_VALUE) {
                final ChunkNibbleArray lightmapBelow = this.getOrAddLightmap(sectionPosBelow);

                final int x = ChunkSectionPos.getLocalCoord(BlockPos.unpackLongX(blockPos));
                final int z = ChunkSectionPos.getLocalCoord(BlockPos.unpackLongZ(blockPos));

                this.changeLightmapComplexity(sectionPosBelow, getComplexityChange(lightmapBelow.get(x, 15, z), oldVal, newVal));
            }
        } else if (this.dirtySections.add(sectionPos)) {
            // Vanilla lightmaps need to be re-parented as they otherwise leak a reference to the old lightmap
            // Dark lightmaps do not leak a reference

            this.storage.replaceWithCopy(sectionPos);

            if (this.vanillaLightmapComplexities.get(sectionPos) != 0) {
                this.updateVanillaLightmapsBelow(sectionPos, this.getLightSection(sectionPos, true));
            }
        }
    }

    @Invoker("isAboveMinHeight")
    @Override
    public abstract boolean callIsAboveMinHeight(int y);

    @Shadow
    protected abstract boolean isAboveMinHeight(final int sectionY);

    @Shadow
    protected abstract boolean isAtOrAboveTopmostSection(long sectionPos);

    @Unique
    private int getHeight(final long chunkPos) {
        return ((SkyLightStorageDataAccess) this.storage).getHeight(chunkPos);
    }

    @Unique
    private int getMinHeight() {
        return ((SkyLightStorageDataAccess) this.storage).getDefaultHeight();
    }

    @Unique
    private void setHeight(final long chunkPos, final int height) {
        ((SkyLightStorageDataAccess) this.storage).setHeight(chunkPos, height + 1);
    }

    @Unique
    private void updateMinHeight(final int y) {
        ((SkyLightStorageDataAccess) this.storage).updateMinHeight(y);
    }

    /**
     * Returns the first section below the provided <code>sectionPos</code> that {@link #hasSection(long) supports light propagations} or {@link Long#MAX_VALUE} if no such section exists.
     */
    @Unique
    private long getSectionBelow(long sectionPos) {
        for (int y = ChunkSectionPos.unpackY(sectionPos); this.isAboveMinHeight(y); --y) {
            if (this.hasSection(sectionPos = ChunkSectionPos.offset(sectionPos, Direction.DOWN))) {
                return sectionPos;
            }
        }

        return Long.MAX_VALUE;
    }

    @Override
    protected int getLightmapComplexityChange(final long blockPos, final int oldVal, final int newVal, final ChunkNibbleArray lightmap) {
        final long sectionPos = ChunkSectionPos.fromBlockPos(blockPos);
        final int x = ChunkSectionPos.getLocalCoord(BlockPos.unpackLongX(blockPos));
        final int y = ChunkSectionPos.getLocalCoord(BlockPos.unpackLongY(blockPos));
        final int z = ChunkSectionPos.getLocalCoord(BlockPos.unpackLongZ(blockPos));

        final int valAbove;

        if (y < 15) {
            valAbove = lightmap.get(x, y + 1, z);
        } else {
            final ChunkNibbleArray lightmapAbove = this.getLightmapAbove(sectionPos);
            valAbove = lightmapAbove == null ? this.getDirectSkylight(sectionPos) : lightmapAbove.get(x, 0, z);
        }

        int amount = getComplexityChange(valAbove, oldVal, newVal);

        if (y > 0) {
            amount += getComplexityChange(lightmap.get(x, y - 1, z), oldVal, newVal);
        }

        return amount;
    }

    /**
     * Returns the first lightmap above the provided <code>sectionPos</code> or <code>null</code> if none exists.
     */
    @Unique
    private ChunkNibbleArray getLightmapAbove(long sectionPos) {
        final long sectionPosAbove = this.getSectionAbove(sectionPos);

        return sectionPosAbove == Long.MAX_VALUE ? null : this.getLightSection(sectionPosAbove, true);
    }

    /**
     * Returns the first section above the provided <code>sectionPos</code> that {@link #hasLightmap(long)}  has a lightmap} or {@link Long#MAX_VALUE} if none exists.
     */
    @Unique
    private long getSectionAbove(long sectionPos) {
        final int height = this.getHeight(ChunkSectionPos.withZeroY(sectionPos));

        if (height != this.getMinHeight()) {
            for (int y = ChunkSectionPos.unpackY(sectionPos) + 1; y < height; ++y) {
                sectionPos = ChunkSectionPosHelper.updateYLong(sectionPos, y);

                if (this.hasLightmap(sectionPos)) {
                    return sectionPos;
                }
            }
        }

        return Long.MAX_VALUE;
    }

    @Unique
    private int getDirectSkylight(final long sectionPos) {
        return this.isSectionEnabled(sectionPos) ? 15 : 0;
    }

    @Override
    protected void beforeLightmapChange(final long sectionPos, final ChunkNibbleArray oldLightmap, final ChunkNibbleArray newLightmap) {
        final long sectionPosBelow = this.getSectionBelow(sectionPos);

        // Adjust Vanilla lightmap complexity
        // If oldLightmap == null, this will be done in onLoadSection()

        final int vanillaComplexity = oldLightmap == null ? 0 : this.initializeVanillaLightmapComplexity(sectionPos, newLightmap);

        // Set up a lightmap and adjust the complexity for the section below

        if (sectionPosBelow != Long.MAX_VALUE) {
            final ChunkNibbleArray lightmapBelow = this.getLightmap(sectionPosBelow);
            final ChunkNibbleArray lightmapAbove = oldLightmap == null ? this.getLightmapAbove(sectionPos) : oldLightmap;

            final int skyLight = this.getDirectSkylight(sectionPos);

            if (lightmapBelow == null) {
                int complexity = 0;

                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) {
                        complexity += Math.abs(newLightmap.get(x, 0, z) - (lightmapAbove == null ? skyLight : lightmapAbove.get(x, 0, z)));
                    }
                }

                if (complexity != 0) {
                    this.getOrAddLightmap(sectionPosBelow);
                    this.setLightmapComplexity(sectionPosBelow, complexity);
                } else if (vanillaComplexity != 0) {
                    // Vanilla lightmaps need to be re-parented as they otherwise leak a reference to the old lightmap
                    // Dark lightmaps do not leak a reference
                    // If oldLightmap == null, this will be done in onLoadSection()

                    this.updateVanillaLightmapsBelow(sectionPos, newLightmap);
                }
            } else {
                int amount = 0;

                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) {
                        amount += getComplexityChange(lightmapBelow.get(x, 15, z), lightmapAbove == null ? skyLight : lightmapAbove.get(x, 0, z), newLightmap.get(x, 0, z));
                    }
                }

                this.changeLightmapComplexity(sectionPosBelow, amount);
            }
        }
    }

    @Override
    protected int getInitialLightmapComplexity(final long sectionPos, final ChunkNibbleArray lightmap) {
        final long sectionPosAbove = this.getSectionAbove(sectionPos);
        final int skyLight = this.getDirectSkylight(sectionPos);

        if (lightmap.isUninitialized()) {
            return sectionPosAbove == Long.MAX_VALUE ? 256 * skyLight : this.vanillaLightmapComplexities.get(sectionPosAbove);
        }

        int complexity = 0;

        for (int y = 0; y < 15; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    complexity += Math.abs(lightmap.get(x, y + 1, z) - lightmap.get(x, y, z));
                }
            }
        }

        final ChunkNibbleArray lightmapAbove = sectionPosAbove == Long.MAX_VALUE ? null : this.getLightSection(sectionPosAbove, true);

        for (int z = 0; z < 16; ++z) {
            for (int x = 0; x < 16; ++x) {
                complexity += Math.abs((lightmapAbove == null ? skyLight : lightmapAbove.get(x, 0, z)) - lightmap.get(x, 15, z));
            }
        }

        return complexity;
    }

    @Unique
    private final Long2IntMap scheduledHeightIncreases = Util.make(new Long2IntOpenHashMap(), map -> map.defaultReturnValue(Integer.MIN_VALUE));

    @Unique
    private final LongSet scheduledHeightChecks = new LongOpenHashSet();

    @Override
    public void setLevel(final long id, final int level) {
        final long chunkPos = ChunkSectionPos.withZeroY(id);

        if (this.enabledChunks.contains(chunkPos)) {
            final int oldLevel = this.getLevel(id);
            final int y = ChunkSectionPos.unpackY(id);

            if (oldLevel != 0 && level == 0) {
                if (y + 1 > this.getHeight(chunkPos)) {
                    if (this.enabledColumns.contains(chunkPos)) {
                        this.setHeight(chunkPos, y);
                    } else if (y > this.scheduledHeightIncreases.get(chunkPos)) {
                        this.scheduledHeightIncreases.put(chunkPos, y);
                        this.markForUpdates();
                    }
                }
            } else if (oldLevel == 0 && level != 0) {
                if (y + 1 == this.getHeight(chunkPos)) {
                    this.scheduledHeightChecks.add(chunkPos);
                    this.markForUpdates();
                }
            } else if (oldLevel >= 2 && level < 2) {
                this.updateMinHeight(y);
            }
        }

        super.setLevel(id, level);
    }

    @Override
    protected ChunkNibbleArray createInitialVanillaLightmap(final long sectionPos) {
        // Attempt to restore data stripped from vanilla saves. See MC-198987

        if (!this.readySections.contains(sectionPos) && !this.readySections.contains(ChunkSectionPos.offset(sectionPos, Direction.UP))) {
            return this.createTrivialVanillaLightmap(sectionPos);
        }

        // A lightmap should have been present in this case unless it was stripped from the vanilla save or the chunk is loaded for the first time.
        // In both cases the lightmap should be initialized with zero.

        final long sectionPosAbove = this.getSectionAbove(sectionPos);
        final int complexity;

        if (sectionPosAbove == Long.MAX_VALUE) {
            complexity = this.isSectionEnabled(sectionPos) ? 15 * 16 * 16 : 0;
        } else {
            complexity = this.vanillaLightmapComplexities.get(sectionPosAbove);
        }

        if (complexity == 0) {
            return EMPTY_SKYLIGHT_MAP;
        }

        // Need to create an actual lightmap in this case as it is non-trivial

        final ChunkNibbleArray lightmap = new ChunkNibbleArray(new byte[2048]);
        this.storage.put(sectionPos, lightmap);
        this.trackSection(sectionPos);
        this.storage.clearCache();

        this.onLoadSection(sectionPos);
        this.setLightmapComplexity(sectionPos, complexity);

        return lightmap;
    }

    @Override
    protected ChunkNibbleArray createTrivialVanillaLightmap(final long sectionPos) {
        final long sectionPosAbove = this.getSectionAbove(sectionPos);

        if (sectionPosAbove == Long.MAX_VALUE) {
            return this.isSectionEnabled(sectionPos) ? DIRECT_SKYLIGHT_MAP : EMPTY_SKYLIGHT_MAP;
        }

        return this.vanillaLightmapComplexities.get(sectionPosAbove) == 0 ? EMPTY_SKYLIGHT_MAP : new SkyLightChunkNibbleArray(this.getLightSection(sectionPosAbove, true));
    }

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    public void onLoadSection(final long sectionPos) {
        final int y = ChunkSectionPos.unpackY(sectionPos);

        this.updateMinHeight(y);
        final long chunkPos = ChunkSectionPos.withZeroY(sectionPos);

        if (y + 1 > this.getHeight(chunkPos)) {
            this.setHeight(chunkPos, y);
        }

        // Vanilla lightmaps need to be re-parented immediately as the old parent can now be modified without informing them

        final ChunkNibbleArray lightmap = this.getLightSection(sectionPos, true);
        this.updateVanillaLightmapsBelow(sectionPos, this.initializeVanillaLightmapComplexity(sectionPos, lightmap) == 0 ? EMPTY_SKYLIGHT_MAP : lightmap);
    }

    @Unique
    private int initializeVanillaLightmapComplexity(final long sectionPos, final ChunkNibbleArray lightmap) {
        int complexity = 0;

        if (!lightmap.isUninitialized()) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    complexity += lightmap.get(x, 0, z);
                }
            }
        }

        this.vanillaLightmapComplexities.put(sectionPos, complexity);

        return complexity;
    }

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    public void onUnloadSection(long sectionPos) {
        // Re-parenting can be deferred as the removed parent is now unmodifiable
        // Dark lightmaps do not leak a reference

        if (this.vanillaLightmapComplexities.remove(sectionPos) != 0) {
            this.removedLightmaps.add(sectionPos);
            this.markForUpdates();
        }

        final long chunkPos = ChunkSectionPos.withZeroY(sectionPos);

        if (ChunkSectionPos.unpackY(sectionPos) + 1 == this.getHeight(chunkPos)) {
            this.scheduledHeightChecks.add(chunkPos);
            this.markForUpdates();
        }
    }

    @Unique
    private void updateVanillaLightmapsBelow(final long sectionPos, final ChunkNibbleArray lightmapAbove) {
        this.removedLightmaps.remove(sectionPos);

        final ChunkNibbleArray lightmap = ((IReadonly) lightmapAbove).isReadonly() ? lightmapAbove : new SkyLightChunkNibbleArray(lightmapAbove);

        for (int y = ChunkSectionPos.unpackY(sectionPos) - 1; this.isAboveMinHeight(y); --y) {
            final long sectionPosBelow = ChunkSectionPos.asLong(ChunkSectionPos.unpackX(sectionPos), y, ChunkSectionPos.unpackZ(sectionPos));

            this.removedLightmaps.remove(sectionPosBelow);

            final ChunkNibbleArray lightmapBelow = this.getLightSection(sectionPosBelow, true);

            if (lightmapBelow == null) {
                continue;
            }

            if (!((IReadonly) lightmapBelow).isReadonly()) {
                break;
            }

            this.storage.put(sectionPosBelow, lightmap);
            this.dirtySections.add(sectionPosBelow);
        }

        this.storage.clearCache();
    }
}
