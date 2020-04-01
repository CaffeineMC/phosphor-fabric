package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.*;
import me.jellysquid.mods.phosphor.common.block.AbstractBlockStateAccess;
import me.jellysquid.mods.phosphor.common.block.ShapeCacheAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.ChunkLightProviderExtended;
import me.jellysquid.mods.phosphor.common.chunk.level.PendingUpdateListener;
import me.jellysquid.mods.phosphor.common.util.cache.LightEngineBlockAccess;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LevelPropagator;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(ChunkLightProvider.class)
public abstract class MixinChunkLightProvider<M extends ChunkToNibbleArrayMap<M>, S extends LightStorage<M>>
        extends LevelPropagator implements ChunkLightProviderExtended, PendingUpdateListener {
    @Shadow
    @Final
    protected BlockPos.Mutable reusableBlockPos;

    @Shadow
    @Final
    protected ChunkProvider chunkProvider;

    private LightEngineBlockAccess blockAccess;

    private final Long2ObjectOpenHashMap<BitSet> buckets = new Long2ObjectOpenHashMap<>();

    private long prevChunkBucketKey = Long.MIN_VALUE;
    private BitSet prevChunkBucketSet;

    protected MixinChunkLightProvider(int levelCount, int expectedLevelSize, int expectedTotalSize) {
        super(levelCount, expectedLevelSize, expectedTotalSize);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(ChunkProvider provider, LightType lightType, S storage, CallbackInfo ci) {
        this.blockAccess = new LightEngineBlockAccess(provider);
    }

    @Inject(method = "clearChunkCache", at = @At("RETURN"))
    private void onCleanup(CallbackInfo ci) {
        // This callback may be executed from the constructor above, and the object won't be initialized then
        if (this.blockAccess != null) {
            this.blockAccess.reset();
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public BlockState getBlockStateForLighting(int x, int y, int z) {
        return this.blockAccess.getBlockState(x, y, z);
    }

    // [VanillaCopy] method_20479
    @Override
    public int getSubtractedLight(BlockState state, int x, int y, int z) {
        ShapeCacheAccess shapeCache = ((AbstractBlockStateAccess) state).getShapeCache();

        if (shapeCache != null) {
            return shapeCache.getLightSubtracted();
        } else {
            return state.getBlock().getOpacity(state, this.chunkProvider.getWorld(), this.reusableBlockPos.set(x, y, z));
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getOpaqueShape(BlockState state, int x, int y, int z, Direction dir) {
        if (state == null || !state.hasSidedTransparency()) {
            return VoxelShapes.empty();
        }

        ShapeCacheAccess shapeCache = ((AbstractBlockStateAccess) state).getShapeCache();

        if (shapeCache != null) {
            VoxelShape[] extrudedFaces = shapeCache.getExtrudedFaces();

            if (extrudedFaces != null) {
                return extrudedFaces[dir.ordinal()];
            }

            return VoxelShapes.empty();
        } else {
            return VoxelShapes.extrudeFace(state.getCullingShape(this.chunkProvider.getWorld(), this.reusableBlockPos.set(x, y, z)), dir);
        }
    }

    /**
     * The vanilla implementation for removing pending light updates requires iterating over either every queued light
     * update (<8K checks) or every block position within a sub-chunk (16^3 checks). This is painfully slow and results
     * in a tremendous amount of CPU time being spent here when chunks are unloaded on the client and server.
     *
     * To work around this, we maintain a bit-field of queued updates by chunk position so we can simply select every
     * light update within a section without excessive iteration. The bit-field only requires 64 bytes of memory per
     * section with queued updates, and does not require expensive hashing in order to track updates within it. In order
     * to avoid as much overhead as possible when looking up a bit-field for a given chunk section, the previous lookup
     * is cached and used where possible. The integer key for each bucket can be computed by performing a simple bit
     * mask over the already-encoded block position value.
     */
    @Override
    public void cancelUpdatesForChunk(long sectionPos) {
        long key = getBucketKeyForSection(sectionPos);
        BitSet bits = this.removeChunkBucket(key);

        if (bits != null && !bits.isEmpty()) {
            int startX = ChunkSectionPos.getX(sectionPos) << 4;
            int startY = ChunkSectionPos.getY(sectionPos) << 4;
            int startZ = ChunkSectionPos.getZ(sectionPos) << 4;

            for (int i = bits.nextSetBit(0); i != -1; i = bits.nextSetBit(i + 1)) {
                int x = (i >> 8) & 15;
                int y = (i >> 4) & 15;
                int z = i & 15;

                this.removePendingUpdate(BlockPos.asLong(startX + x, startY + y, startZ + z));
            }
        }
    }

    @Override
    public void onPendingUpdateRemoved(long blockPos) {
        long key = getBucketKeyForBlock(blockPos);

        BitSet bits;

        if (this.prevChunkBucketKey == key) {
            bits = this.prevChunkBucketSet;
        } else {
            bits = this.buckets.get(key);

            if (bits == null) {
                return;
            }
        }

        bits.clear(getLocalIndex(blockPos));

        if (bits.isEmpty()) {
            this.removeChunkBucket(key);
        }
    }

    @Override
    public void onPendingUpdateAdded(long blockPos) {
        long key = getBucketKeyForBlock(blockPos);

        BitSet bits;

        if (this.prevChunkBucketKey == key) {
            bits = this.prevChunkBucketSet;
        } else {
            bits = this.buckets.get(key);

            if (bits == null) {
                this.buckets.put(key, bits = new BitSet(16 * 16 * 16));
            }

            this.prevChunkBucketKey = key;
            this.prevChunkBucketSet = bits;
        }

        bits.set(getLocalIndex(blockPos));
    }

    // Used to mask a long-encoded block position into a bucket key by dropping the first 4 bits of each component
    private static final long BLOCK_TO_BUCKET_KEY_MASK = ~BlockPos.asLong(15, 15, 15);

    private long getBucketKeyForBlock(long blockPos) {
        return blockPos & BLOCK_TO_BUCKET_KEY_MASK;
    }

    private long getBucketKeyForSection(long sectionPos) {
        return BlockPos.asLong(ChunkSectionPos.getX(sectionPos) << 4, ChunkSectionPos.getY(sectionPos) << 4, ChunkSectionPos.getZ(sectionPos) << 4);
    }

    private BitSet removeChunkBucket(long key) {
        BitSet set = this.buckets.remove(key);

        if (this.prevChunkBucketSet == set) {
            this.prevChunkBucketKey = Long.MIN_VALUE;
            this.prevChunkBucketSet = null;
        }

        return set;
    }

    // Finds the bit-flag index of a local position within a chunk section
    private static int getLocalIndex(long blockPos) {
        int x = BlockPos.unpackLongX(blockPos) & 15;
        int y = BlockPos.unpackLongY(blockPos) & 15;
        int z = BlockPos.unpackLongZ(blockPos) & 15;

        return (x << 8) | (y << 4) | z;
    }
}
