package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedChunkLightProvider;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedGenericLightStorage;
import me.jellysquid.mods.phosphor.common.util.math.DirectionHelper;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.BlockLightStorage;
import net.minecraft.world.chunk.light.ChunkBlockLightProvider;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static net.minecraft.util.math.ChunkSectionPos.toChunkCoord;

@SuppressWarnings("rawtypes")
@Mixin(ChunkBlockLightProvider.class)
public abstract class MixinChunkBlockLightProvider extends ChunkLightProvider<BlockLightStorage.Data, BlockLightStorage> {
    public MixinChunkBlockLightProvider(ChunkProvider chunkProvider, LightType type, BlockLightStorage lightStorage) {
        super(chunkProvider, type, lightStorage);
    }

    @Shadow
    protected abstract int getLightSourceLuminance(long blockPos);

    @Shadow
    @Final
    private static Direction[] DIRECTIONS_BLOCKLIGHT;

    /**
     * This breaks up the call to method_20479 into smaller parts so we do not have to pass a mutable heap object
     * to the method in order to extract the light result. This has a few other advantages, allowing us to:
     * - Avoid the de-optimization that occurs from allocating and passing a heap object
     * - Avoid unpacking coordinates twice for both the call to method_20479 and method_20710.
     * - Avoid the the specific usage of AtomicInteger, which has additional overhead for the atomic get/set operations.
     * - Avoid checking if the checked block is opaque twice.
     * <p>
     * The rest of the implementation has been otherwise copied from vanilla, but is optimized to avoid constantly
     * (un)packing coordinates and to use an optimized direction lookup function.
     *
     * @author JellySquid
     */
    @Override
    @Overwrite
    public int getPropagatedLevel(long fromId, long toId, int currentLevel) {
        if (toId == Long.MAX_VALUE) {
            return 15;
        } else if (fromId == Long.MAX_VALUE) {
            return currentLevel + 15 - this.getLightSourceLuminance(toId);
        } else if (currentLevel >= 15) {
            return currentLevel;
        }

        int bX = BlockPos.unpackLongX(toId);
        int bY = BlockPos.unpackLongY(toId);
        int bZ = BlockPos.unpackLongZ(toId);

        int aX = BlockPos.unpackLongX(fromId);
        int aY = BlockPos.unpackLongY(fromId);
        int aZ = BlockPos.unpackLongZ(fromId);

        Direction dir = DirectionHelper.getVecDirection(bX - aX, bY - aY, bZ - aZ);

        if (dir != null) {
            BlockState bState = ((ExtendedChunkLightProvider) this).getBlockStateForLighting(bX, bY, bZ);

            if (bState == null) {
                return 15;
            }

            int newLevel = ((ExtendedChunkLightProvider) this).getSubtractedLight(bState, bX, bY, bZ);

            if (newLevel >= 15) {
                return 15;
            }

            VoxelShape bShape = ((ExtendedChunkLightProvider) this).getVoxelShape(bState, bX, bY, bZ, dir.getOpposite());
            VoxelShape aShape = ((ExtendedChunkLightProvider) this).getVoxelShape(aX, aY, aZ, dir);

            if (!VoxelShapes.method_20713(aShape, bShape)) {
                return currentLevel + Math.max(1, newLevel);
            }
        }

        return 15;
    }

    /**
     * Avoids constantly (un)packing coordinates. This strictly copies vanilla's implementation.
     *
     * @author JellySquid
     */
    @Override
    @Overwrite
    public void updateNeighborsRecursively(long id, int targetLevel, boolean mergeAsMin) {
        int x = BlockPos.unpackLongX(id);
        int y = BlockPos.unpackLongY(id);
        int z = BlockPos.unpackLongZ(id);

        long chunk = ChunkSectionPos.asLong(toChunkCoord(x), toChunkCoord(y), toChunkCoord(z));

        for (Direction dir : DIRECTIONS_BLOCKLIGHT) {
            int adjX = x + dir.getOffsetX();
            int adjY = y + dir.getOffsetY();
            int adjZ = z + dir.getOffsetZ();

            long adjChunk = ChunkSectionPos.asLong(toChunkCoord(adjX), toChunkCoord(adjY), toChunkCoord(adjZ));

            if ((chunk == adjChunk) || ((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(adjChunk)) {
                this.updateRecursively(id, BlockPos.asLong(adjX, adjY, adjZ), targetLevel, mergeAsMin);
            }
        }
    }
}
