package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.phosphor.common.block.BlockStateLightInfo;
import me.jellysquid.mods.phosphor.common.block.BlockStateLightInfoAccess;
import me.jellysquid.mods.phosphor.common.chunk.level.LevelUpdateListener;
import me.jellysquid.mods.phosphor.common.chunk.light.InitialLightingAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.LightInitializer;
import me.jellysquid.mods.phosphor.common.chunk.light.LightProviderBlockAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.LightProviderUpdateTracker;
import me.jellysquid.mods.phosphor.common.chunk.light.LightStorageAccess;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LevelPropagator;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.BitSet;

@Mixin(ChunkLightProvider.class)
public abstract class MixinChunkLightProvider<M extends ChunkToNibbleArrayMap<M>, S extends LightStorage<M>>
        extends LevelPropagator implements LightProviderUpdateTracker, LightProviderBlockAccess, LightInitializer, LevelUpdateListener, InitialLightingAccess {
    private static final BlockState DEFAULT_STATE = Blocks.AIR.getDefaultState();
    private static final ChunkSection[] EMPTY_SECTION_ARRAY = new ChunkSection[16];

    @Shadow
    @Final
    protected BlockPos.Mutable reusableBlockPos;

    @Shadow
    @Final
    protected ChunkProvider chunkProvider;

    private final long[] cachedChunkPos = new long[2];
    private final ChunkSection[][] cachedChunkSections = new ChunkSection[2][];

    private final Long2ObjectOpenHashMap<BitSet> buckets = new Long2ObjectOpenHashMap<>();

    private long prevChunkBucketKey = ChunkPos.MARKER;
    private BitSet prevChunkBucketSet;

    protected MixinChunkLightProvider(int levelCount, int expectedLevelSize, int expectedTotalSize) {
        super(levelCount, expectedLevelSize, expectedTotalSize);
    }

    @Inject(method = "clearChunkCache", at = @At("RETURN"))
    private void onCleanup(CallbackInfo ci) {
        // This callback may be executed from the constructor above, and the object won't be initialized then
        if (this.cachedChunkPos != null) {
            Arrays.fill(this.cachedChunkPos, ChunkPos.MARKER);
            Arrays.fill(this.cachedChunkSections, null);
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public BlockState getBlockStateForLighting(int x, int y, int z) {
        if (this.chunkProvider.getWorld().isOutOfHeightLimit(y)) {
            return DEFAULT_STATE;
        }

        final long chunkPos = ChunkPos.toLong(x >> 4, z >> 4);

        for (int i = 0; i < 2; i++) {
            if (this.cachedChunkPos[i] == chunkPos) {
                return this.getBlockStateFromSection(this.cachedChunkSections[i], x, y, z);
            }
        }

        return this.getBlockStateForLightingUncached(x, y, z);
    }

    private BlockState getBlockStateForLightingUncached(int x, int y, int z) {
        return this.getBlockStateFromSection(this.getAndCacheChunkSections(x >> 4, z >> 4), x, y, z);
    }

    private BlockState getBlockStateFromSection(ChunkSection[] sections, int x, int y, int z) {
        ChunkSection section = sections[y >> 4];

        if (section != null) {
            return section.getBlockState(x & 15, y & 15, z & 15);
        }

        return DEFAULT_STATE;
    }

    private ChunkSection[] getAndCacheChunkSections(int x, int z) {
        final Chunk chunk = (Chunk) this.chunkProvider.getChunk(x, z);
        final ChunkSection[] sections = chunk != null ? chunk.getSectionArray() : EMPTY_SECTION_ARRAY;

        final ChunkSection[][] cachedSections = this.cachedChunkSections;
        cachedSections[1] = cachedSections[0];
        cachedSections[0] = sections;

        final long[] cachedCoords = this.cachedChunkPos;
        cachedCoords[1] = cachedCoords[0];
        cachedCoords[0] = ChunkPos.toLong(x, z);

        return sections;
    }

    // [VanillaCopy] method_20479
    @Override
    public int getSubtractedLight(BlockState state, int x, int y, int z) {
        BlockStateLightInfo info = ((BlockStateLightInfoAccess) state).getLightInfo();

        if (info != null) {
            return info.getLightSubtracted();
        } else {
            return this.getSubtractedLightFallback(state, x, y, z);
        }
    }

    private int getSubtractedLightFallback(BlockState state, int x, int y, int z) {
        return state.getBlock().getOpacity(state, this.chunkProvider.getWorld(), this.reusableBlockPos.set(x, y, z));
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getOpaqueShape(BlockState state, int x, int y, int z, Direction dir) {
        if (state != null && state.hasSidedTransparency()) {
            BlockStateLightInfo info = ((BlockStateLightInfoAccess) state).getLightInfo();

            if (info != null) {
                VoxelShape[] extrudedFaces = info.getExtrudedFaces();

                if (extrudedFaces != null) {
                    return extrudedFaces[dir.ordinal()];
                }
            } else {
                return this.getOpaqueShapeFallback(state, x, y, z, dir);
            }
        }

        return VoxelShapes.empty();
    }

    private VoxelShape getOpaqueShapeFallback(BlockState state, int x, int y, int z, Direction dir) {
        return VoxelShapes.extrudeFace(state.getCullingShape(this.chunkProvider.getWorld(), this.reusableBlockPos.set(x, y, z)), dir);
    }

    @Override
    public void spreadLightInto(long a, long b) {
        this.updateLevel(a, b, this.getPropagatedLevel(a, b, this.getLevel(a)), false);
        this.updateLevel(b, a, this.getPropagatedLevel(b, a, this.getLevel(b)), false);
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
            int startX = ChunkSectionPos.unpackX(sectionPos) << 4;
            int startY = ChunkSectionPos.unpackY(sectionPos) << 4;
            int startZ = ChunkSectionPos.unpackZ(sectionPos) << 4;

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
        return BlockPos.asLong(ChunkSectionPos.unpackX(sectionPos) << 4, ChunkSectionPos.unpackY(sectionPos) << 4, ChunkSectionPos.unpackZ(sectionPos) << 4);
    }

    private BitSet removeChunkBucket(long key) {
        BitSet set = this.buckets.remove(key);

        if (this.prevChunkBucketSet == set) {
            this.prevChunkBucketKey = ChunkPos.MARKER;
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

    @Shadow
    @Final
    protected LightStorage<?> lightStorage;

    /**
     * @author PhiPro
     * @reason Re-implement completely. Change specification of the method.
     * Now controls both source light and light updates. Disabling now additionally removes all light data associated to the chunk.
     */
    @SuppressWarnings("ConstantConditions")
    @Overwrite
    public void setColumnEnabled(final ChunkPos pos, final boolean enabled) {
        final long chunkPos = ChunkSectionPos.withZeroY(ChunkSectionPos.asLong(pos.x, 0, pos.z));
        final LightStorageAccess lightStorage = (LightStorageAccess) this.lightStorage;

        if (enabled) {
            lightStorage.invokeSetColumnEnabled(chunkPos, true);
            lightStorage.enableLightUpdates(chunkPos);
        } else {
            lightStorage.disableChunkLight(chunkPos, (ChunkLightProvider<?, ?>) (Object) this);
        }
    }

    @Override
    public void enableSourceLight(final long chunkPos) {
        ((LightStorageAccess) this.lightStorage).invokeSetColumnEnabled(chunkPos, true);
    }

    @Override
    public void enableLightUpdates(final long chunkPos) {
        ((LightStorageAccess) this.lightStorage).enableLightUpdates(chunkPos);
    }
}
