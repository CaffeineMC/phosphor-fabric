package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedGenericLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedMixinChunkLightProvider;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorage;
import me.jellysquid.mods.phosphor.common.util.BlockPosHelper;
import me.jellysquid.mods.phosphor.common.util.ChunkSectionPosHelper;
import me.jellysquid.mods.phosphor.common.util.PhosphorDirection;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.ChunkSkyLightProvider;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static net.minecraft.util.math.ChunkSectionPos.toChunkCoord;
import static net.minecraft.util.math.ChunkSectionPos.toLocalCoord;

@Mixin(ChunkSkyLightProvider.class)
public abstract class MixinChunkSkyLightProvider extends ChunkLightProvider<SkyLightStorage.Data, SkyLightStorage> {
    @Shadow
    @Final
    private static Direction[] HORIZONTAL_DIRECTIONS;

    public MixinChunkSkyLightProvider(ChunkProvider chunkProvider_1, LightType lightType_1, SkyLightStorage lightStorage_1) {
        super(chunkProvider_1, lightType_1, lightStorage_1);
    }

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
        }

        if (a == Long.MAX_VALUE) {
            if (!((ExtendedSkyLightStorage) this.lightStorage).bridge$method_15565(b)) {
                return 15;
            }

            level = 0;
        } else if (level >= 15) {
            return level;
        }

        int bX = BlockPos.unpackLongX(b);
        int bY = BlockPos.unpackLongY(b);
        int bZ = BlockPos.unpackLongZ(b);

        int aX = BlockPos.unpackLongX(a);
        int aY = BlockPos.unpackLongY(a);
        int aZ = BlockPos.unpackLongZ(a);

        BlockState bState = ((ExtendedMixinChunkLightProvider) this).getBlockStateForLighting(bX, bY, bZ);

        if (bState == null) {
            return 15;
        }

        int newLight = ((ExtendedMixinChunkLightProvider) this).getSubtractedLight(bState, bX, bY, bZ);

        boolean sameXZ = aX == bX && aZ == bZ;

        Direction dir;

        if (a == Long.MAX_VALUE) {
            dir = Direction.DOWN;
        } else {
            dir = PhosphorDirection.getVecDirection(bX - aX, bY - aY, bZ - aZ);
        }

        if (dir != null) {
            VoxelShape aShape = ((ExtendedMixinChunkLightProvider) this).getVoxelShape(aX, aY, aZ, dir);
            VoxelShape bShape = ((ExtendedMixinChunkLightProvider) this).getVoxelShape(bState, bX, bY, bZ, dir.getOpposite());

            if (VoxelShapesHelper.method_20713_fast(aShape, bShape)) {
                return 15;
            }
        } else {
            dir = PhosphorDirection.getVecDirection(bX - aX, sameXZ ? -1 : 0, bZ - aZ);

            if (dir == null) {
                return 15;
            }

            VoxelShape aShape = ((ExtendedMixinChunkLightProvider) this).getVoxelShape(aX, aY, aZ, Direction.DOWN);

            if (aShape == VoxelShapes.empty()) {
                return 15;
            }

            VoxelShape bShape = ((ExtendedMixinChunkLightProvider) this).getVoxelShape(bState, bX, bY, bZ, dir.getOpposite());

            if (bShape == VoxelShapes.empty()) {
                return 15;
            }
        }

        if ((a == Long.MAX_VALUE || sameXZ && aY > bY) && level == 0 && newLight == 0) {
            return 0;
        } else {
            return level + Math.max(1, newLight);
        }
    }

    /**
     * A few key optimizations are made here, in particular:
     * - The code avoids un-packing coordinates as much as possible and stores the results into local variables.
     * - When necessary, coordinate re-packing is reduced to the minimum number of operations. Most of them can be reduced
     * to only updating the Y-coordinate versus re-computing the entire integer.
     * - Coordinate re-packing is removed where unnecessary (such as when only comparing the Y-coordinate of two positions)
     * <p>
     * This copies the vanilla implementation as close as possible.
     *
     * @author JellySquid
     */
    @Override
    @Overwrite
    public void updateNeighborsRecursively(long longPos, int level, boolean flag) {
        int posX = BlockPos.unpackLongX(longPos);
        int posY = BlockPos.unpackLongY(longPos);
        int posZ = BlockPos.unpackLongZ(longPos);

        int chunkY = toChunkCoord(posY);

        long chunk = ChunkSectionPos.asLong(toChunkCoord(posX), chunkY, toChunkCoord(posZ));

        int n = 0;

        if (toLocalCoord(posY) == 0) {
            while (((ExtendedSkyLightStorage) this.lightStorage).bridge$isAboveMinimumHeight(toChunkCoord(posY) - n - 1) &&
                    !((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(ChunkSectionPosHelper.updateYLong(chunk, toChunkCoord(posY + (-n - 1))))) {
                ++n;
            }
        }

        int nY = posY - 1 - (n * 16);
        int nChunkY = toChunkCoord(nY);

        if (chunkY == nChunkY || ((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(ChunkSectionPosHelper.updateYLong(chunk, nChunkY))) {
            this.updateRecursively(longPos, BlockPosHelper.updateYLong(longPos, nY), level, flag);
        }

        int upChunkY = toChunkCoord(posY + 1);

        if (chunkY == upChunkY || ((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(ChunkSectionPosHelper.updateYLong(chunk, upChunkY))) {
            this.updateRecursively(longPos, BlockPosHelper.updateYLong(longPos, posY + 1), level, flag);
        }

        for (Direction dir : HORIZONTAL_DIRECTIONS) {
            int k = 0;

            int adjPosX = posX + dir.getOffsetX();
            int adjPosZ = posZ + dir.getOffsetZ();

            while (true) {
                int adjPosY = posY - k;

                long adjChunkPos = ChunkSectionPos.asLong(toChunkCoord(adjPosX), toChunkCoord(adjPosY), toChunkCoord(adjPosZ));

                if (adjChunkPos == chunk) {
                    this.updateRecursively(longPos, BlockPos.asLong(adjPosX, adjPosY, adjPosZ), level, flag);

                    break;
                }

                if (((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(adjChunkPos)) {
                    this.updateRecursively(longPos, BlockPos.asLong(adjPosX, adjPosY, adjPosZ), level, flag);
                }

                ++k;

                if (k > n * 16) {
                    break;
                }
            }
        }

    }
}
