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

import java.util.concurrent.locks.StampedLock;

@Mixin(SkyLightStorage.class)
public abstract class MixinSkyLightStorage extends MixinLightStorage<SkyLightStorage.Data> {
    /**
     * An optimized implementation which avoids constantly unpacking and repacking integer coordinates.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @SuppressWarnings({"unchecked"})
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

            int height = sdata.getHeight(ChunkSectionPos.withZeroZ(chunk));

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
    protected abstract boolean isAboveTopmostLightArray(long sectionPos);

    @Shadow
    protected abstract boolean isLightEnabled(long sectionPos);

    @Override
    public int getLightWithoutLightmap(final long blockPos)
    {
        long sectionPos = ChunkSectionPos.offset(ChunkSectionPos.fromGlobalPos(blockPos), Direction.UP);

        if (this.isAboveTopmostLightArray(sectionPos)) {
            return this.isLightEnabled(sectionPos) ? 15 : 0;
        }

        ChunkNibbleArray lightmap;

        for (; (lightmap = this.getLightArray(sectionPos, true)) == null; sectionPos = ChunkSectionPos.offset(sectionPos, Direction.UP));

        return lightmap.get(ChunkSectionPos.getLocalCoord(BlockPos.unpackLongX(blockPos)), 0, ChunkSectionPos.getLocalCoord(BlockPos.unpackLongZ(blockPos)));
    }
}
