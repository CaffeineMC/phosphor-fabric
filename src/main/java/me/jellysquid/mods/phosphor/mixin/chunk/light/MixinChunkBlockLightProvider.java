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
    public MixinChunkBlockLightProvider(ChunkProvider chunkProvider_1, LightType lightType_1, BlockLightStorage lightStorage_1) {
        super(chunkProvider_1, lightType_1, lightStorage_1);
    }

    @Shadow
    protected abstract int getLightSourceLuminance(long long_1);

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
    public int getPropagatedLevel(long a, long b, int level) {
        if (b == Long.MAX_VALUE) {
            return 15;
        } else if (a == Long.MAX_VALUE) {
            return level + 15 - this.getLightSourceLuminance(b);
        } else if (level >= 15) {
            return level;
        }

        int bX = BlockPos.unpackLongX(b);
        int bY = BlockPos.unpackLongY(b);
        int bZ = BlockPos.unpackLongZ(b);

        int aX = BlockPos.unpackLongX(a);
        int aY = BlockPos.unpackLongY(a);
        int aZ = BlockPos.unpackLongZ(a);

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
                return level + Math.max(1, newLevel);
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
    public void updateNeighborsRecursively(long longPos, int int_1, boolean boolean_1) {
        int x = BlockPos.unpackLongX(longPos);
        int y = BlockPos.unpackLongY(longPos);
        int z = BlockPos.unpackLongZ(longPos);

        long chunk = ChunkSectionPos.asLong(toChunkCoord(x), toChunkCoord(y), toChunkCoord(z));

        for (Direction dir : DIRECTIONS_BLOCKLIGHT) {
            int adjX = x + dir.getOffsetX();
            int adjY = y + dir.getOffsetY();
            int adjZ = z + dir.getOffsetZ();

            long adjChunk = ChunkSectionPos.asLong(toChunkCoord(adjX), toChunkCoord(adjY), toChunkCoord(adjZ));

            if ((chunk == adjChunk) || ((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(adjChunk)) {
                this.updateRecursively(longPos, BlockPos.asLong(adjX, adjY, adjZ), int_1, boolean_1);
            }
        }
    }
}
