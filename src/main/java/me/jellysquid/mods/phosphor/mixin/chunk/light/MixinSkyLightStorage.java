package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.light.LightStorageAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.SkyLightStorageDataAccess;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SkyLightStorage.class)
public abstract class MixinSkyLightStorage {
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
        int posY = BlockPos.unpackLongY(pos);
        int posZ = BlockPos.unpackLongZ(pos);

        int chunkX = ChunkSectionPos.getSectionCoord(posX);
        int chunkY = ChunkSectionPos.getSectionCoord(posY);
        int chunkZ = ChunkSectionPos.getSectionCoord(posZ);

        long chunk = ChunkSectionPos.asLong(chunkX, chunkY, chunkZ);

        SkyLightStorage.Data data = ((LightStorageAccess<SkyLightStorage.Data>) this).getStorageUncached();
        SkyLightStorageDataAccess sdata = ((SkyLightStorageDataAccess) (Object) data);

        int height = sdata.getHeightMap().get(ChunkSectionPos.withZeroZ(chunk));

        if (height == sdata.getDefaultHeight() || chunkY >= height) {
            return 15;
        }

        ChunkNibbleArray array = data.get(chunk);

        while (array == null) {
            ++chunkY;

            if (chunkY >= height) {
                return 15;
            }

            chunk = ChunkSectionPosHelper.updateYLong(chunk, chunkY);
            array = data.get(chunk);

            posY = chunkY << 4;
        }

        return array.get(
                ChunkSectionPos.getLocalCoord(posX),
                ChunkSectionPos.getLocalCoord(posY),
                ChunkSectionPos.getLocalCoord(posZ)
        );
    }
}
