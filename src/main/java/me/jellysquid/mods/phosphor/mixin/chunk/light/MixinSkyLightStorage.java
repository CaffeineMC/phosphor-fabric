package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.jellysquid.mods.phosphor.common.chunk.light.IReadonly;
import me.jellysquid.mods.phosphor.common.chunk.light.LevelPropagatorAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SharedLightStorageAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SkyLightStorageDataAccess;
import me.jellysquid.mods.phosphor.common.util.chunk.light.EmptyChunkNibbleArray;
import me.jellysquid.mods.phosphor.common.util.chunk.light.SkyLightChunkNibbleArray;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;

@Mixin(SkyLightStorage.class)
public abstract class MixinSkyLightStorage extends MixinLightStorage<SkyLightStorage.Data> {
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

        StampedLock lock = ((SharedLightStorageAccess<SkyLightStorage.Data>) this).getStorageLock();
        long stamp;

        ChunkNibbleArray array;

        optimisticRead:
        while (true) {
            stamp = lock.tryOptimisticRead();

            int posY = posYOrig;
            int chunkY = chunkYOrig;
            long chunk = chunkOrig;

            SkyLightStorage.Data data = ((SharedLightStorageAccess<SkyLightStorage.Data>) this).getStorage();
            SkyLightStorageDataAccess sdata = ((SkyLightStorageDataAccess) (Object) data);

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
    protected abstract boolean isAtOrAboveTopmostSection(long sectionPos);

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

    @Inject(
        method = "enqueueRemoveSection(J)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disable_enqueueRemoveSection(final CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(
        method = "enqueueAddSection(J)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disable_enqueueAddSection(final CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Forceload a lightmap above the world for initial skylight
     */
    @Unique
    private final LongSet preInitSkylightChunks = new LongOpenHashSet();

    @Override
    public void beforeChunkEnabled(final long chunkPos) {
        if (!this.isSectionEnabled(chunkPos)) {
            this.preInitSkylightChunks.add(chunkPos);
            this.updateLevel(Long.MAX_VALUE, ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), 16, ChunkSectionPos.unpackZ(chunkPos)), 1, true);
        }
    }

    @Override
    public void afterChunkDisabled(final long chunkPos) {
        if (this.preInitSkylightChunks.remove(chunkPos)) {
            this.updateLevel(Long.MAX_VALUE, ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), 16, ChunkSectionPos.unpackZ(chunkPos)), 2, false);
        }
    }

    @Override
    protected int getInitialLevel(final long id) {
        final int ret = super.getInitialLevel(id);

        if (ret >= 2 && ChunkSectionPos.unpackY(id) == 16 && this.preInitSkylightChunks.contains(ChunkSectionPos.withZeroY(id))) {
            return 1;
        }

        return ret;
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
            if (this.preInitSkylightChunks.contains(chunkPos)) {
                this.initSkylightChunks.add(chunkPos);
                this.checkForUpdates();
            } else {
                this.enabledColumns.add(chunkPos);
            }
        } else {
            this.enabledColumns.remove(chunkPos);
            this.initSkylightChunks.remove(chunkPos);
            this.checkForUpdates();
        }
    }

    @Unique
    private static void spreadSourceSkylight(final LevelPropagatorAccess lightProvider, final long src, final Direction dir) {
        lightProvider.invokePropagateLevel(src, BlockPos.offset(src, dir), 0, true);
    }

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    public void updateLight(ChunkLightProvider<SkyLightStorage.Data, ?> lightProvider, boolean doSkylight, boolean skipEdgeLightPropagation) {
        super.updateLight(lightProvider, doSkylight, skipEdgeLightPropagation);

        if (!doSkylight || !this.hasUpdates) {
            return;
        }

        this.lightChunks(lightProvider);
        this.updateRemovedLightmaps();

        this.hasUpdates = false;
    }

    @Unique
    private void lightChunks(final ChunkLightProvider<SkyLightStorage.Data, ?> lightProvider) {
        if (this.initSkylightChunks.isEmpty()) {
            return;
        }

        final LevelPropagatorAccess levelPropagator = (LevelPropagatorAccess) lightProvider;

        for (final LongIterator it = this.initSkylightChunks.iterator(); it.hasNext(); ) {
            final long chunkPos = it.nextLong();

            final int minY = this.fillSkylightColumn(lightProvider, chunkPos);

            this.enabledColumns.add(chunkPos);
            this.preInitSkylightChunks.remove(chunkPos);
            this.updateLevel(Long.MAX_VALUE, ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), 16, ChunkSectionPos.unpackZ(chunkPos)), 2, false);

            if (this.hasSection(ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), minY, ChunkSectionPos.unpackZ(chunkPos)))) {
                final long blockPos = BlockPos.asLong(ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackX(chunkPos)), ChunkSectionPos.getBlockCoord(minY), ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackZ(chunkPos)));

                for (int x = 0; x < 16; ++x) {
                    for (int z = 0; z < 16; ++z) {
                        spreadSourceSkylight(levelPropagator, BlockPos.add(blockPos, x, 16, z), Direction.DOWN);
                    }
                }
            }

            for (final Direction dir : Direction.Type.HORIZONTAL) {
                // Skip propagations into sections directly exposed to skylight that are initialized in this update cycle
                boolean spread = !this.initSkylightChunks.contains(ChunkSectionPos.offset(chunkPos, dir));

                for (int y = 16; y > minY; --y) {
                    final long sectionPos = ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), y, ChunkSectionPos.unpackZ(chunkPos));
                    final long neighborSectionPos = ChunkSectionPos.offset(sectionPos, dir);

                    if (!this.hasSection(neighborSectionPos)) {
                        continue;
                    }

                    if (!spread) {
                        if (this.readySections.contains(neighborSectionPos)) {
                            spread = true;
                        } else {
                            continue;
                        }
                    }

                    final long blockPos = BlockPos.asLong(ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackX(sectionPos)), ChunkSectionPos.getBlockCoord(y), ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackZ(sectionPos)));

                    final int ox = 15 * Math.max(dir.getOffsetX(), 0);
                    final int oz = 15 * Math.max(dir.getOffsetZ(), 0);

                    final int dx = Math.abs(dir.getOffsetZ());
                    final int dz = Math.abs(dir.getOffsetX());

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
        if (this.removedLightmaps.isEmpty()) {
            return;
        }

        final LongSet removedLightmaps = new LongOpenHashSet(this.removedLightmaps);

        for (final LongIterator it = removedLightmaps.iterator(); it.hasNext(); ) {
            final long sectionPos = it.nextLong();

            if (!this.enabledChunks.contains(ChunkSectionPos.withZeroY(sectionPos))) {
                continue;
            }

            if (!this.removedLightmaps.contains(sectionPos)) {
                continue;
            }

            final long sectionPosAbove = this.getSectionAbove(sectionPos);

            if (sectionPosAbove == Long.MAX_VALUE) {
                this.updateVanillaLightmapsBelow(sectionPos, this.isSectionEnabled(sectionPos) ? DIRECT_SKYLIGHT_MAP : null, true);
            } else {
                long removedLightmapPosAbove = sectionPos;

                for (long pos = sectionPos; pos != sectionPosAbove; pos = ChunkSectionPos.offset(pos, Direction.UP)) {
                    if (this.removedLightmaps.remove(pos)) {
                        removedLightmapPosAbove = pos;
                    }
                }

                this.updateVanillaLightmapsBelow(removedLightmapPosAbove, this.vanillaLightmapComplexities.get(sectionPosAbove) == 0 ? null : this.getLightSection(sectionPosAbove, true), false);
            }
        }

        this.removedLightmaps.clear();
    }

    /**
     * Fill all sections above the topmost block with source skylight.
     * @return The section containing the topmost block or the section corresponding to {@link SkyLightStorage.Data#minSectionY} if none exists.
     */
    private int fillSkylightColumn(final ChunkLightProvider<SkyLightStorage.Data, ?> lightProvider, final long chunkPos) {
        int minY = 16;
        ChunkNibbleArray lightmapAbove = null;

        // First need to remove all pending light updates before changing any light value

        for (; this.isAboveMinHeight(minY); --minY) {
            final long sectionPos = ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), minY, ChunkSectionPos.unpackZ(chunkPos));

            if (this.readySections.contains(sectionPos)) {
                break;
            }

            if (this.hasSection(sectionPos)) {
                this.removeSection(lightProvider, sectionPos);
            }

            final ChunkNibbleArray lightmap = this.getLightmap(sectionPos);

            if (lightmap != null) {
                lightmapAbove = lightmap;
            }
        }

        // Set up a lightmap and adjust the complexity for the section below

        final long sectionPosBelow = ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), minY, ChunkSectionPos.unpackZ(chunkPos));

        if (this.hasSection(sectionPosBelow)) {
            final ChunkNibbleArray lightmapBelow = this.getLightmap(sectionPosBelow);

            if (lightmapBelow == null) {
                int complexity = 15 * 16 * 16;

                if (lightmapAbove != null) {
                    for (int z = 0; z < 16; ++z) {
                        for (int x = 0; x < 16; ++x) {
                            complexity -= lightmapAbove.get(x, 0, z);
                        }
                    }
                }

                this.getOrAddLightmap(sectionPosBelow);
                this.setLightmapComplexity(sectionPosBelow, complexity);
            } else {
                int amount = 0;

                for (int z = 0; z < 16; ++z) {
                    for (int x = 0; x < 16; ++x) {
                        amount += getComplexityChange(lightmapBelow.get(x, 15, z), lightmapAbove == null ? 0 : lightmapAbove.get(x, 0, z), 15);
                    }
                }

                this.changeLightmapComplexity(sectionPosBelow, amount);
            }
        }

        // Now light values can be changed
        // Delete lightmaps so the sections inherit direct skylight

        int sections = 0;

        for (int y = 16; y > minY; --y) {
            final long sectionPos = ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), y, ChunkSectionPos.unpackZ(chunkPos));

            if (this.removeLightmap(sectionPos)) {
                sections |= 1 << (y + 1);
            }
        }

        // Calling onUnloadSection() after removing all the lightmaps is slightly more efficient

        this.storage.clearCache();

        for (int y = 16; y > minY; --y) {
            if ((sections & (1 << (y + 1))) != 0) {
                this.onUnloadSection(ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), y, ChunkSectionPos.unpackZ(chunkPos)));
            }
        }

        // Add trivial lightmaps for vanilla compatibility

        for (int y = 16; y > minY; --y) {
            final long sectionPos = ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), y, ChunkSectionPos.unpackZ(chunkPos));

            if (this.nonOptimizableSections.contains(sectionPos)) {
                this.storage.put(sectionPos, this.createTrivialVanillaLightmap(DIRECT_SKYLIGHT_MAP));
                this.dirtySections.add(sectionPos);
            }
        }

        this.storage.clearCache();

        return minY;
    }

    @Shadow
    private volatile boolean hasUpdates;

    /**
     * @author PhiPro
     * @reason Re-implement completely
     */
    @Overwrite
    private void checkForUpdates() {
        this.hasUpdates = !this.initSkylightChunks.isEmpty();
    }

    @Unique
    private static final ChunkNibbleArray DIRECT_SKYLIGHT_MAP = createDirectSkyLightMap();

    @Unique
    private final Long2IntMap vanillaLightmapComplexities = new Long2IntOpenHashMap();
    @Unique
    private final LongSet removedLightmaps = new LongOpenHashSet();

    @Unique
    private static ChunkNibbleArray createDirectSkyLightMap() {
        final ChunkNibbleArray lightmap = new ChunkNibbleArray();
        Arrays.fill(lightmap.asByteArray(), (byte) -1);

        return lightmap;
    }

    @Override
    public boolean hasSection(final long sectionPos) {
        return super.hasSection(sectionPos) && this.getLightSection(sectionPos, true) != null;
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
        }

        // Vanilla lightmaps need to be re-parented as they otherwise leak a reference to the old lightmap

        if (this.dirtySections.add(sectionPos)) {
            this.storage.replaceWithCopy(sectionPos);
            this.updateVanillaLightmapsBelow(sectionPos, this.getLightSection(sectionPos, true), false);
        }
    }

    @Shadow
    protected abstract boolean isAboveMinHeight(final int sectionY);

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
        sectionPos = ChunkSectionPos.offset(sectionPos, Direction.UP);

        if (this.isAtOrAboveTopmostSection(sectionPos)) {
            return Long.MAX_VALUE;
        }

        while (!this.hasLightmap(sectionPos)) {
            sectionPos = ChunkSectionPos.offset(sectionPos, Direction.UP);
        }

        return sectionPos;
    }

    @Unique
    private int getDirectSkylight(final long sectionPos) {
        return this.isSectionEnabled(sectionPos) ? 15 : 0;
    }

    @Override
    protected void beforeLightmapChange(final long sectionPos, final ChunkNibbleArray oldLightmap, final ChunkNibbleArray newLightmap) {
        final long sectionPosBelow = this.getSectionBelow(sectionPos);

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

        // Vanilla lightmaps need to be re-parented as they otherwise leak a reference to the old lightmap

        this.updateVanillaLightmapsOnLightmapCreation(sectionPos, newLightmap);
    }

    @Override
    protected int getInitialLightmapComplexity(final long sectionPos, final ChunkNibbleArray lightmap) {
        int complexity = 0;

        for (int y = 0; y < 15; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    complexity += Math.abs(lightmap.get(x, y + 1, z) - lightmap.get(x, y, z));
                }
            }
        }

        final ChunkNibbleArray lightmapAbove = this.getLightmapAbove(sectionPos);
        final int skyLight = this.getDirectSkylight(sectionPos);

        for (int z = 0; z < 16; ++z) {
            for (int x = 0; x < 16; ++x) {
                complexity += Math.abs((lightmapAbove == null ? skyLight : lightmapAbove.get(x, 0, z)) - lightmap.get(x, 15, z));
            }
        }

        return complexity;
    }

    @Redirect(
        method = "onUnloadSection(J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/light/SkyLightStorage;hasSection(J)Z"
        )
    )
    private boolean hasActualLightmap(final SkyLightStorage lightStorage, long sectionPos) {
        return this.hasLightmap(sectionPos);
    }

    @Override
    public void setLevel(final long id, final int level) {
        final int oldLevel = this.getLevel(id);

        if (oldLevel >= 2 && level < 2) {
            ((SkyLightStorageDataAccess) (Object) this.storage).updateMinHeight(ChunkSectionPos.unpackY(id));
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
            return this.createTrivialVanillaLightmap(null);
        }

        // Need to create an actual lightmap in this case as it is non-trivial

        final ChunkNibbleArray lightmap = new ChunkNibbleArray(new byte[2048]);
        this.storage.put(sectionPos, lightmap);
        this.storage.clearCache();

        this.onLoadSection(sectionPos);
        this.setLightmapComplexity(sectionPos, complexity);

        return lightmap;
    }

    @Override
    protected ChunkNibbleArray createTrivialVanillaLightmap(final long sectionPos) {
        final long sectionPosAbove = this.getSectionAbove(sectionPos);

        if (sectionPosAbove == Long.MAX_VALUE) {
            return this.createTrivialVanillaLightmap(this.isSectionEnabled(sectionPos) ? DIRECT_SKYLIGHT_MAP : null);
        }

        return this.createTrivialVanillaLightmap(this.vanillaLightmapComplexities.get(sectionPosAbove) == 0 ? null : this.getLightSection(sectionPosAbove, true));
    }

    @Unique
    private ChunkNibbleArray createTrivialVanillaLightmap(final ChunkNibbleArray lightmapAbove) {
        return lightmapAbove == null ? new EmptyChunkNibbleArray() : new SkyLightChunkNibbleArray(lightmapAbove);
    }

    @Inject(
        method = "onLoadSection(J)V",
        at = @At("HEAD")
    )
    private void updateVanillaLightmapsOnLightmapCreation(final long sectionPos, final CallbackInfo ci) {
        this.updateVanillaLightmapsOnLightmapCreation(sectionPos, this.getLightSection(sectionPos, true));
    }

    @Unique
    private void updateVanillaLightmapsOnLightmapCreation(final long sectionPos, final ChunkNibbleArray lightmap) {
        int complexity = 0;

        for (int z = 0; z < 16; ++z) {
            for (int x = 0; x < 16; ++x) {
                complexity += lightmap.get(x, 0, z);
            }
        }

        this.vanillaLightmapComplexities.put(sectionPos, complexity);
        this.removedLightmaps.remove(sectionPos);

        // Enabling the chunk already creates all relevant vanilla lightmaps

        if (!this.enabledChunks.contains(ChunkSectionPos.withZeroY(sectionPos))) {
            return;
        }

        // Vanilla lightmaps need to be re-parented immediately as the old parent can now be modified without informing them

        this.updateVanillaLightmapsBelow(sectionPos, complexity == 0 ? null : lightmap, false);
    }

    @Inject(
        method = "onUnloadSection(J)V",
        at = @At("HEAD")
    )
    private void updateVanillaLightmapsOnLightmapRemoval(final long sectionPos, final CallbackInfo ci) {
        this.vanillaLightmapComplexities.remove(sectionPos);

        if (!this.enabledChunks.contains(ChunkSectionPos.withZeroY(sectionPos))) {
            return;
        }

        // Re-parenting can be deferred as the removed parent is now unmodifiable

        this.removedLightmaps.add(sectionPos);
    }

    @Unique
    private void updateVanillaLightmapsBelow(final long sectionPos, final ChunkNibbleArray lightmapAbove, final boolean stopOnRemovedLightmap) {
        for (int y = ChunkSectionPos.unpackY(sectionPos) - 1; this.isAboveMinHeight(y); --y) {
            final long sectionPosBelow = ChunkSectionPos.asLong(ChunkSectionPos.unpackX(sectionPos), y, ChunkSectionPos.unpackZ(sectionPos));

            if (stopOnRemovedLightmap) {
                if (this.removedLightmaps.contains(sectionPosBelow)) {
                    break;
                }
            } else {
                this.removedLightmaps.remove(sectionPosBelow);
            }

            final ChunkNibbleArray lightmapBelow = this.getLightSection(sectionPosBelow, true);

            if (lightmapBelow == null) {
                continue;
            }

            if (!((IReadonly) lightmapBelow).isReadonly()) {
                break;
            }

            this.storage.put(sectionPosBelow, this.createTrivialVanillaLightmap(lightmapAbove));
            this.dirtySections.add(sectionPosBelow);
        }

        this.storage.clearCache();
    }
}
