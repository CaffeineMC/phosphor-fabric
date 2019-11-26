package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorageData;
import me.jellysquid.mods.phosphor.common.util.ChunkSectionPosHelper;
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
    protected abstract boolean method_15565(long long_1);

    @Shadow
    protected abstract boolean isAboveMinimumHeight(int int_1);

    @Override
    public boolean bridge$method_15565(long long_1) {
        return this.method_15565(long_1);
    }

    @Override
    public boolean bridge$isAboveMinimumHeight(int y) {
        return this.isAboveMinimumHeight(y);
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

        int chunkX = ChunkSectionPos.toChunkCoord(posX);
        int chunkY = ChunkSectionPos.toChunkCoord(posY);
        int chunkZ = ChunkSectionPos.toChunkCoord(posZ);

        long chunk = ChunkSectionPos.asLong(chunkX, chunkY, chunkZ);

        SkyLightStorage.Data data = (SkyLightStorage.Data) ((ExtendedLightStorage) this).bridge$getStorageUncached();

        int h = ((ExtendedSkyLightStorageData) (Object) data).bridge$heightMap().get(ChunkSectionPos.toLightStorageIndex(chunk));

        if (h != ((ExtendedSkyLightStorageData) (Object) data).bridge$defaultHeight() && chunkY < h) {
            ChunkNibbleArray array = ((ExtendedLightStorage) this).bridge$getDataForChunk(data, chunk);

            if (array == null) {
                posY &= ~16;

                while (array == null) {
                    ++chunkY;

                    if (chunkY >= h) {
                        return 15;
                    }

                    chunk = ChunkSectionPosHelper.updateYLong(chunk, chunkY);
                    posY += 16;
                    array = ((ExtendedLightStorage) this).bridge$getDataForChunk(data, chunk);
                }
            }

            return array.get(
                    ChunkSectionPos.toLocalCoord(posX),
                    ChunkSectionPos.toLocalCoord(posY),
                    ChunkSectionPos.toLocalCoord(posZ)
            );
        } else {
            return 15;
        }
    }
}
