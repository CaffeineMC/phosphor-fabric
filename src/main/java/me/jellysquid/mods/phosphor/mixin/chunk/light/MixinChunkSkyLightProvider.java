package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.ExtendedChunkLightProvider;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedGenericLightStorage;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedSkyLightStorage;
import me.jellysquid.mods.phosphor.common.util.math.ChunkSectionPosHelper;
import me.jellysquid.mods.phosphor.common.util.math.DirectionHelper;
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

    @Shadow
    @Final
    private static Direction[] DIRECTIONS_SKYLIGHT;

    public MixinChunkSkyLightProvider(ChunkProvider chunkProvider, LightType type, SkyLightStorage lightStorage) {
        super(chunkProvider, type, lightStorage);
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
    public int getPropagatedLevel(long fromId, long toId, int currentLevel) {
        if (toId == Long.MAX_VALUE) {
            return 15;
        }

        if (fromId == Long.MAX_VALUE) {
            if (((ExtendedSkyLightStorage) this.lightStorage).bridge$method_15565(toId)) {
                currentLevel = 0;
            } else {
                return 15;
            }
        }

        if (currentLevel >= 15) {
            return currentLevel;
        }

        int toX = BlockPos.unpackLongX(toId);
        int toY = BlockPos.unpackLongY(toId);
        int toZ = BlockPos.unpackLongZ(toId);

        BlockState toState = ((ExtendedChunkLightProvider) this).getBlockStateForLighting(toX, toY, toZ);

        if (toState == null) {
            return 15;
        }

        int fromX = BlockPos.unpackLongX(fromId);
        int fromY = BlockPos.unpackLongY(fromId);
        int fromZ = BlockPos.unpackLongZ(fromId);

        boolean verticalOnly = fromX == toX && fromZ == toZ;

        Direction dir;

        if (fromId == Long.MAX_VALUE) {
            dir = Direction.DOWN;
        } else {
            dir = DirectionHelper.getVecDirection(toX - fromX, toY - fromY, toZ - fromZ);
        }

        if (dir != null) {
            VoxelShape toShape = ((ExtendedChunkLightProvider) this).getVoxelShape(toState, toX, toY, toZ, dir.getOpposite());

            if (toShape != VoxelShapes.fullCube()) {
                VoxelShape fromShape = ((ExtendedChunkLightProvider) this).getVoxelShape(fromX, fromY, fromZ, dir);

                if (VoxelShapes.method_20713(fromShape, toShape)) {
                    return 15;
                }
            }
        } else {
            Direction altDir = Direction.fromVector(toX - fromX, verticalOnly ? -1 : 0, toZ - fromZ);

            if (altDir == null) {
                return 15;
            }

            VoxelShape toShape = ((ExtendedChunkLightProvider) this).getVoxelShape(toState, toX, toY, toZ, altDir.getOpposite());

            if (VoxelShapes.method_20713(VoxelShapes.empty(), toShape)) {
                return 15;
            }

            VoxelShape fromShape = ((ExtendedChunkLightProvider) this).getVoxelShape(fromX, fromY, fromZ, Direction.DOWN);

            if (VoxelShapes.method_20713(fromShape, VoxelShapes.empty())) {
                return 15;
            }
        }

        int out = ((ExtendedChunkLightProvider) this).getSubtractedLight(toState, toX, toY, toZ);

        if ((fromId == Long.MAX_VALUE || verticalOnly && fromY > toY) && currentLevel == 0 && out == 0) {
            return 0;
        } else {
            return currentLevel + Math.max(1, out);
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
    public void updateNeighborsRecursively(long id, int targetLevel, boolean mergeAsMin) {
        long chunkId = ChunkSectionPos.toChunkLong(id);

        int x = BlockPos.unpackLongX(id);
        int y = BlockPos.unpackLongY(id);
        int z = BlockPos.unpackLongZ(id);

        int localX = toLocalCoord(x);
        int localY = toLocalCoord(y);
        int localZ = toLocalCoord(z);

        if (localX > 0 && localX < 15 && localY > 0 && localY < 15 && localZ > 0 && localZ < 15) {
            for (Direction dir : DIRECTIONS_SKYLIGHT) {
                this.updateRecursively(id, BlockPos.asLong(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ()), targetLevel, mergeAsMin);
            }
        } else {
            int chunkY = toChunkCoord(y);
            int n = 0;

            if (localY == 0) {
                while (!((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(ChunkSectionPos.offsetPacked(chunkId, 0, -n - 1, 0))
                        && ((ExtendedSkyLightStorage) this.lightStorage).bridge$isAboveMinimumHeight(chunkY - n - 1)) {
                    ++n;
                }
            }

            int belowY = y + (-1 - n * 16);
            int belowChunkY = toChunkCoord(belowY);

            if (chunkY == belowChunkY || ((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(ChunkSectionPosHelper.updateYLong(chunkId, belowChunkY))) {
                this.updateRecursively(id, BlockPos.asLong(x, belowY, z), targetLevel, mergeAsMin);
            }

            int aboveY = y + 1;
            int aboveChunkY = toChunkCoord(aboveY);

            if (chunkY == aboveChunkY || ((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(ChunkSectionPosHelper.updateYLong(chunkId, aboveChunkY))) {
                this.updateRecursively(id, BlockPos.asLong(x, aboveY, z), targetLevel, mergeAsMin);
            }

            for (Direction dir : HORIZONTAL_DIRECTIONS) {
                int adjX = x + dir.getOffsetX();
                int adjZ = z + dir.getOffsetZ();

                int offsetY = 0;

                while (true) {
                    int adjY = y - offsetY;

                    long offsetId = BlockPos.asLong(adjX, adjY, adjZ);
                    long offsetChunkId = ChunkSectionPos.toChunkLong(offsetId);

                    if (chunkId == offsetChunkId) {
                        this.updateRecursively(id, offsetId, targetLevel, mergeAsMin);

                        break;
                    }

                    if (((ExtendedGenericLightStorage) this.lightStorage).bridge$hasChunk(offsetChunkId)) {
                        this.updateRecursively(id, offsetId, targetLevel, mergeAsMin);
                    }

                    ++offsetY;

                    if (offsetY > n * 16) {
                        break;
                    }
                }
            }
        }

    }
}
