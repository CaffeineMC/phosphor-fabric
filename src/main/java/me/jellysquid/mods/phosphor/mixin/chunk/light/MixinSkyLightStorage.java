package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorageData;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SkyLightStorage.class)
public abstract class MixinSkyLightStorage implements ExtendedSkyLightStorage {
    @Shadow
    protected abstract boolean method_15565(long l);

    @Shadow
    protected abstract boolean isAboveMinimumHeight(int blockY);

    @Override
    public boolean bridge$method_15565(long l) {
        return this.method_15565(l);
    }

    @Override
    public boolean bridge$isAboveMinimumHeight(int blockY) {
        return this.isAboveMinimumHeight(blockY);
    }

    /**
     * An optimized implementation which avoids constantly unpacking and repacking integer coordinates.
     *
     * @author JellySquid
     */
    @SuppressWarnings({"ConstantConditions", "unchecked"})
    @Overwrite
    public int getLight(long pos) {
        int posX = BlockPos.unpackLongX(pos);
        int posY = BlockPos.unpackLongY(pos);
        int posZ = BlockPos.unpackLongZ(pos);

        int chunkX = ChunkSectionPos.getSectionCoord(posX);
        int chunkY = ChunkSectionPos.getSectionCoord(posY);
        int chunkZ = ChunkSectionPos.getSectionCoord(posZ);

        long chunk = ChunkSectionPos.asLong(chunkX, chunkY, chunkZ);

        SkyLightStorage.Data data = ((ExtendedLightStorage<SkyLightStorage.Data>) this).bridge$getStorageUncached();

        int h = ((ExtendedSkyLightStorageData) (Object) data).bridge$heightMap().get(ChunkSectionPos.withZeroZ(chunk));

        if (h != ((ExtendedSkyLightStorageData) (Object) data).bridge$defaultHeight() && chunkY < h) {
            ChunkNibbleArray array = ((ExtendedLightStorage<SkyLightStorage.Data>) this).bridge$getDataForChunk(data, chunk);

            if (array == null) {
                posY &= -16;

                while (array == null) {
                    ++chunkY;

                    if (chunkY >= h) {
                        return 15;
                    }

                    chunk = ChunkSectionPosHelper.updateYLong(chunk, chunkY);
                    posY += 16;
                    array = ((ExtendedLightStorage<SkyLightStorage.Data>) this).bridge$getDataForChunk(data, chunk);
                }
            }

            return array.get(
                    ChunkSectionPos.getLocalCoord(posX),
                    ChunkSectionPos.getLocalCoord(posY),
                    ChunkSectionPos.getLocalCoord(posZ)
            );
        } else {
            return 15;
        }
    }
}
