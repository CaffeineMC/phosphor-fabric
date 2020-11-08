package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.jellysquid.mods.phosphor.common.chunk.light.LevelPropagatorAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SharedLightStorageAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SkyLightStorageDataAccess;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
    public int getLightWithoutLightmap(final long blockPos)
    {
        long sectionPos = ChunkSectionPos.offset(ChunkSectionPos.fromBlockPos(blockPos), Direction.UP);

        if (this.isAtOrAboveTopmostSection(sectionPos)) {
            return this.isSectionEnabled(sectionPos) ? 15 : 0;
        }

        ChunkNibbleArray lightmap;

        while ((lightmap = this.getLightSection(sectionPos, true)) == null) {
            sectionPos = ChunkSectionPos.offset(sectionPos, Direction.UP);
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
    public void prepareInitialLighting(final long chunkPos) {
        this.preInitSkylightChunks.add(chunkPos);
        this.updateLevel(Long.MAX_VALUE, ChunkSectionPos.asLong(ChunkSectionPos.unpackX(chunkPos), 16, ChunkSectionPos.unpackZ(chunkPos)), 1, true);
    }

    @Override
    public void cancelInitialLighting(final long chunkPos) {
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
     * @reason Re-implement completely
     */
    @Overwrite
    public void setColumnEnabled(final long chunkPos, final boolean enabled) {
        if (enabled) {
            if (preInitSkylightChunks.contains(chunkPos)) {
                initSkylightChunks.add(chunkPos);
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

        for (final LongIterator it = this.initSkylightChunks.iterator(); it.hasNext(); ) {
            final long chunkPos = it.nextLong();

            final LevelPropagatorAccess levelPropagator = (LevelPropagatorAccess) lightProvider;
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

        this.initSkylightChunks.clear();

        this.hasUpdates = false;
    }

    /**
     * Fill all sections above the topmost block with source skylight.
     * @return The section containing the topmost block or the section corresponding to {@link SkyLightStorage.Data#minSectionY} if none exists.
     */
    private int fillSkylightColumn(final ChunkLightProvider<SkyLightStorage.Data, ?> lightProvider, final long chunkPos) {
        // TODO: Implement
        return 0;
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
}
