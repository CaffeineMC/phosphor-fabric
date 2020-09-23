package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.level.LevelPropagatorExtended;
import me.jellysquid.mods.phosphor.common.chunk.light.BlockLightStorageAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.LightProviderBlockAccess;
import me.jellysquid.mods.phosphor.common.util.LightUtil;
import me.jellysquid.mods.phosphor.common.util.math.DirectionHelper;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.BlockLightStorage;
import net.minecraft.world.chunk.light.ChunkBlockLightProvider;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static net.minecraft.util.math.ChunkSectionPos.getSectionCoord;

@Mixin(ChunkBlockLightProvider.class)
public abstract class MixinChunkBlockLightProvider extends ChunkLightProvider<BlockLightStorage.Data, BlockLightStorage>
        implements LevelPropagatorExtended, LightProviderBlockAccess {
    public MixinChunkBlockLightProvider(ChunkProvider chunkProvider, LightType type, BlockLightStorage lightStorage) {
        super(chunkProvider, type, lightStorage);
    }

    @Shadow
    protected abstract int getLightSourceLuminance(long blockPos);

    @Shadow
    @Final
    private static Direction[] DIRECTIONS;

    /**
     * @reason Use optimized variant
     * @author JellySquid
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
     * <p>
     * The rest of the implementation has been otherwise copied from vanilla, but is optimized to avoid constantly
     * (un)packing coordinates and to use an optimized direction lookup function.
     *
     * @param fromState The re-usable block state at position {@param fromId}
     * @author JellySquid
     */
    @Override
    public int getPropagatedLevel(long fromId, BlockState fromState, long toId, int currentLevel) {
        if (toId == Long.MAX_VALUE) {
            return 15;
        } else if (fromId == Long.MAX_VALUE && ((BlockLightStorageAccess) this.lightStorage).isLightEnabled(ChunkSectionPos.fromBlockPos(toId))) {
            // Disable blocklight sources before initial lighting
            return currentLevel + 15 - this.getLightSourceLuminance(toId);
        } else if (currentLevel >= 15) {
            return currentLevel;
        }

        int toX = BlockPos.unpackLongX(toId);
        int toY = BlockPos.unpackLongY(toId);
        int toZ = BlockPos.unpackLongZ(toId);

        int fromX = BlockPos.unpackLongX(fromId);
        int fromY = BlockPos.unpackLongY(fromId);
        int fromZ = BlockPos.unpackLongZ(fromId);

        Direction dir = DirectionHelper.getVecDirection(toX - fromX, toY - fromY, toZ - fromZ);

        if (dir != null) {
            BlockState toState = this.getBlockStateForLighting(toX, toY, toZ);

            if (toState == null) {
                return 15;
            }

            int newLevel = this.getSubtractedLight(toState, toX, toY, toZ);

            if (newLevel >= 15) {
                return 15;
            }

            if (fromState == null) {
                fromState = this.getBlockStateForLighting(fromX, fromY, fromZ);
            }

            VoxelShape aShape = this.getOpaqueShape(fromState, fromX, fromY, fromZ, dir);
            VoxelShape bShape = this.getOpaqueShape(toState, toX, toY, toZ, dir.getOpposite());

            if (!LightUtil.unionCoversFullCube(aShape, bShape)) {
                return currentLevel + Math.max(1, newLevel);
            }
        }

        return 15;
    }

    /**
     * Avoids constantly (un)packing coordinates. This strictly copies vanilla's implementation.
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Override
    @Overwrite
    public void propagateLevel(long id, int targetLevel, boolean mergeAsMin) {
        int x = BlockPos.unpackLongX(id);
        int y = BlockPos.unpackLongY(id);
        int z = BlockPos.unpackLongZ(id);

        long chunk = ChunkSectionPos.asLong(getSectionCoord(x), getSectionCoord(y), getSectionCoord(z));

        BlockState state = this.getBlockStateForLighting(x, y, z);

        for (Direction dir : DIRECTIONS) {
            int adjX = x + dir.getOffsetX();
            int adjY = y + dir.getOffsetY();
            int adjZ = z + dir.getOffsetZ();

            long adjChunk = ChunkSectionPos.asLong(getSectionCoord(adjX), getSectionCoord(adjY), getSectionCoord(adjZ));

            if ((chunk == adjChunk) || this.lightStorage.hasSection(adjChunk)) {
                this.propagateLevel(id, state, BlockPos.asLong(adjX, adjY, adjZ), targetLevel, mergeAsMin);
            }
        }
    }
}
