package net.caffeinemc.phosphor.mixin.chunk.light;

import net.caffeinemc.phosphor.common.chunk.light.SkyLightStorageAccess;
import net.caffeinemc.phosphor.common.util.LightUtil;
import net.caffeinemc.phosphor.common.util.math.ChunkSectionPosHelper;
import net.caffeinemc.phosphor.common.util.math.DirectionHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.light.ChunkSkyLightProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static net.minecraft.util.math.ChunkSectionPos.getLocalCoord;
import static net.minecraft.util.math.ChunkSectionPos.getSectionCoord;

@Mixin(ChunkSkyLightProvider.class)
public abstract class MixinChunkSkyLightProvider extends MixinChunkLightProvider {
    private static final BlockState AIR_BLOCK = Blocks.AIR.getDefaultState();

    @Shadow
    @Final
    private static Direction[] HORIZONTAL_DIRECTIONS;

    @Shadow
    @Final
    private static Direction[] DIRECTIONS;

    /**
     * @author JellySquid
     * @reason Use optimized method below
     */
    @Override
    @Overwrite
    public int getPropagatedLevel(long fromId, long toId, int currentLevel) {
        return this.getPropagatedLevel(fromId, null, toId, currentLevel);
    }

    /**
     * This breaks up the call to method_20479 into smaller parts so we do not have to pass a mutable heap object
     * to the method in order to extract the light result. This has a few other advantages, allowing us to:
     * - Avoid the de-optimization that occurs from allocating and passing a heap object
     * - Avoid unpacking coordinates twice for both the call to method_20479 and method_20710.
     * - Avoid the the specific usage of AtomicInteger, which has additional overhead for the atomic get/set operations.
     * - Avoid checking if the checked block is opaque twice.
     * - Avoid a redundant block state lookup by re-using {@param fromState}
     *
     * The rest of the implementation has been otherwise copied from vanilla, but is optimized to avoid constantly
     * (un)packing coordinates and to use an optimized direction lookup function.
     *
     * @param fromState The re-usable block state at position {@param fromId}
     */
    @Override
    public int getPropagatedLevel(long fromId, BlockState fromState, long toId, int currentLevel) {
        if (toId == Long.MAX_VALUE || fromId == Long.MAX_VALUE) {
            return 15;
        } else if (currentLevel >= 15) {
            return currentLevel;
        }

        int toX = BlockPos.unpackLongX(toId);
        int toY = BlockPos.unpackLongY(toId);
        int toZ = BlockPos.unpackLongZ(toId);

        BlockState toState = this.getBlockStateForLighting(toX, toY, toZ);

        int fromX = BlockPos.unpackLongX(fromId);
        int fromY = BlockPos.unpackLongY(fromId);
        int fromZ = BlockPos.unpackLongZ(fromId);

        if (fromState == null) {
            fromState = this.getBlockStateForLighting(fromX, fromY, fromZ);
        }

        // Most light updates will happen between two empty air blocks, so use this to assume some properties
        boolean airPropagation = toState == AIR_BLOCK && fromState == AIR_BLOCK;
        boolean verticalOnly = fromX == toX && fromZ == toZ;

        // The direction the light update is propagating
        Direction dir = DirectionHelper.getVecDirection(toX - fromX, toY - fromY, toZ - fromZ);

        if (dir == null) {
            throw new IllegalStateException(String.format("Light was spread in illegal direction %d, %d, %d", Integer.signum(toX - fromX), Integer.signum(toY - fromY), Integer.signum(toZ - fromZ)));
        }

        // Shape comparison checks are only meaningful if the blocks involved have non-empty shapes
        // If we're comparing between air blocks, this is meaningless
        if (!airPropagation) {
            VoxelShape toShape = this.getOpaqueShape(toState, toX, toY, toZ, dir.getOpposite());

            if (toShape != VoxelShapes.fullCube()) {
                VoxelShape fromShape = this.getOpaqueShape(fromState, fromX, fromY, fromZ, dir);

                if (LightUtil.unionCoversFullCube(fromShape, toShape)) {
                    return 15;
                }
            }
        }

        int out = this.getSubtractedLight(toState, toX, toY, toZ);

        if (out == 0 && currentLevel == 0 && verticalOnly && fromY > toY) {
            return 0;
        }

        return currentLevel + Math.max(1, out);
    }

    /**
     * A few key optimizations are made here, in particular:
     * - The code avoids un-packing coordinates as much as possible and stores the results into local variables.
     * - When necessary, coordinate re-packing is reduced to the minimum number of operations. Most of them can be reduced
     * to only updating the Y-coordinate versus re-computing the entire integer.
     * - Coordinate re-packing is removed where unnecessary (such as when only comparing the Y-coordinate of two positions)
     * - A special propagation method is used that allows the BlockState at {@param id} to be passed, allowing the code
     * which follows to simply re-use it instead of redundantly retrieving another block state.
     *
     * Additionally this implements the cleanups discussed in MC-196542. In particular:
     * - This always passes adjacent positions to {@link #propagateLevel(long, BlockState, long, int, boolean)}
     * - This reduces map lookups in the skylight optimization code
     *
     * @reason Use faster implementation
     * @author JellySquid, PhiPro
     */
    @Overwrite
    public void propagateLevel(long id, int targetLevel, boolean mergeAsMin) {
        long chunkId = ChunkSectionPos.fromBlockPos(id);

        int x = BlockPos.unpackLongX(id);
        int y = BlockPos.unpackLongY(id);
        int z = BlockPos.unpackLongZ(id);

        int localX = getLocalCoord(x);
        int localY = getLocalCoord(y);
        int localZ = getLocalCoord(z);

        BlockState fromState = this.getBlockStateForLighting(x, y, z);

        // Fast-path: Use much simpler logic if we do not need to access adjacent chunks
        if (localX > 0 && localX < 15 && localY > 0 && localY < 15 && localZ > 0 && localZ < 15) {
            for (Direction dir : DIRECTIONS) {
                this.propagateLevel(id, fromState, BlockPos.asLong(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ()), targetLevel, mergeAsMin);
            }

            return;
        }

        int chunkY = getSectionCoord(y);
        int chunkOffsetY = 0;

        // Skylight optimization: Try to find bottom-most non-empty chunk
        if (localY == 0) {
            while (!this.hasSection(ChunkSectionPos.offset(chunkId, 0, -chunkOffsetY - 1, 0))
                    && ((SkyLightStorageAccess) this.lightStorage).callIsAboveMinHeight(chunkY - chunkOffsetY - 1)) {
                ++chunkOffsetY;
            }
        }

        int belowY = y + (-1 - chunkOffsetY * 16);
        int belowChunkY = getSectionCoord(belowY);

        if (chunkY == belowChunkY || this.hasSection(ChunkSectionPosHelper.updateYLong(chunkId, belowChunkY))) {
            // MC-196542: Pass adjacent source position
            BlockState state = chunkY == belowChunkY ? fromState : AIR_BLOCK;
            this.propagateLevel(BlockPos.asLong(x, belowY + 1, z), state, BlockPos.asLong(x, belowY, z), targetLevel, mergeAsMin);
        }

        int aboveY = y + 1;
        int aboveChunkY = getSectionCoord(aboveY);

        if (chunkY == aboveChunkY || this.hasSection(ChunkSectionPosHelper.updateYLong(chunkId, aboveChunkY))) {
            this.propagateLevel(id, fromState, BlockPos.asLong(x, aboveY, z), targetLevel, mergeAsMin);
        }

        for (Direction dir : HORIZONTAL_DIRECTIONS) {
            int adjX = x + dir.getOffsetX();
            int adjZ = z + dir.getOffsetZ();

            long offsetId = BlockPos.asLong(adjX, y, adjZ);
            long offsetChunkId = ChunkSectionPos.fromBlockPos(offsetId);

            boolean isWithinOriginChunk = chunkId == offsetChunkId;

            if (isWithinOriginChunk || this.hasSection(offsetChunkId)) {
                this.propagateLevel(id, fromState, offsetId, targetLevel, mergeAsMin);
            }

            if (isWithinOriginChunk) {
                continue;
            }

            // MC-196542: First iterate over sections to reduce map lookups
            for (int offsetChunkY = chunkY - 1; offsetChunkY > belowChunkY; --offsetChunkY) {
                if (!this.hasSection(ChunkSectionPosHelper.updateYLong(offsetChunkId, offsetChunkY))) {
                    continue;
                }

                for (int offsetY = 15; offsetY >= 0; --offsetY) {
                    int adjY = ChunkSectionPos.getBlockCoord(offsetChunkY) + offsetY;
                    offsetId = BlockPos.asLong(adjX, adjY, adjZ);

                    this.propagateLevel(BlockPos.asLong(x, adjY, z), AIR_BLOCK, offsetId, targetLevel, mergeAsMin);
                }
            }
        }
    }

    /**
     * @author PhiPro
     * @reason MC-196542: There is no need to reset any level other than the directly requested position.
     */
    @Overwrite
    public void resetLevel(long id) {
        super.resetLevel(id);
    }
}
