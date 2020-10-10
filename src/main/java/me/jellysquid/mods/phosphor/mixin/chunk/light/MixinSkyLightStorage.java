package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.SharedLightStorageAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SkyLightStorageDataAccess;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
