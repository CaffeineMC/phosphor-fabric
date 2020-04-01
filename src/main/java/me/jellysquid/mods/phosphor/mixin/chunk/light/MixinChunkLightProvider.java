package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.phosphor.common.block.BlockStateAccess;
import me.jellysquid.mods.phosphor.common.block.ShapeCacheAccess;
import me.jellysquid.mods.phosphor.common.chunk.light.ChunkLightProviderExtended;
import me.jellysquid.mods.phosphor.common.chunk.level.PendingUpdateListener;
import me.jellysquid.mods.phosphor.common.util.cache.LightEngineBlockAccess;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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

import java.util.Arrays;
import java.util.BitSet;

@Mixin(ChunkLightProvider.class)
public abstract class MixinChunkLightProvider<M extends ChunkToNibbleArrayMap<M>, S extends LightStorage<M>>
        extends LevelPropagator implements ChunkLightProviderExtended, PendingUpdateListener {
    private static long GLOBAL_TO_CHUNK_MASK = ~BlockPos.asLong(0xF, 0xF, 0xF);

    @Shadow
    @Final
    protected BlockPos.Mutable reusableBlockPos;

    @Shadow
    @Final
    protected ChunkProvider chunkProvider;

    private LightEngineBlockAccess blockAccess;

    private Long2ObjectOpenHashMap<BitSet> pendingUpdatesByChunk;

    private BitSet[] lastChunkUpdateSets;
    private long[] lastChunkPos;

    protected MixinChunkLightProvider(int levelCount, int expectedLevelSize, int expectedTotalSize) {
        super(levelCount, expectedLevelSize, expectedTotalSize);
    }


    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(ChunkProvider provider, LightType lightType, S storage, CallbackInfo ci) {
        this.blockAccess = new LightEngineBlockAccess(provider);
        this.pendingUpdatesByChunk = new Long2ObjectOpenHashMap<>(512, 0.25F);

        this.lastChunkUpdateSets = new BitSet[2];
        this.lastChunkPos = new long[2];

        this.resetUpdateSetCache();

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
        if (y < 0 || y >= 256) {
            return Blocks.AIR.getDefaultState();
        }

        return this.blockAccess.getBlockState(x, y, z);
    }

    // [VanillaCopy] method_20479
    @Override
    public int getSubtractedLight(BlockState state, int x, int y, int z) {
        ShapeCacheAccess shapeCache = ((BlockStateAccess) state).getShapeCache();

        if (shapeCache != null) {
            return shapeCache.getLightSubtracted();
        } else {
            return state.getOpacity(this.chunkProvider.getWorld(), this.reusableBlockPos.set(x, y, z));
        }
    }

    // [VanillaCopy] method_20479
    @Override
    public VoxelShape getOpaqueShape(BlockState state, int x, int y, int z, Direction dir) {
        if (state == null) {
            return VoxelShapes.empty();
        }

        ShapeCacheAccess shapeCache = ((BlockStateAccess) state).getShapeCache();

        if (shapeCache != null) {
            VoxelShape[] extrudedFaces = shapeCache.getExtrudedFaces();

            if (extrudedFaces != null) {
                return extrudedFaces[dir.ordinal()];
            }

            return VoxelShapes.empty();
        } else {
            return state.getCullingFace(this.chunkProvider.getWorld(), this.reusableBlockPos.set(x, y, z), dir);
        }
    }

    /**
     * The vanilla implementation for removing pending light updates requires iterating over either every queued light
     * update (<8K checks) or every block position within a sub-chunk (16^3 checks). This is painfully slow and results
     * in a tremendous amount of CPU time being spent here when chunks are unloaded on the client and server.
     *
     * To work around this, we maintain a list of queued updates by chunk position so we can simply select every light
     * update within a chunk and drop them in one operation.
     */
    @Override
    public void cancelUpdatesForChunk(long sectionPos) {
        int chunkX = ChunkSectionPos.getX(sectionPos);
        int chunkY = ChunkSectionPos.getY(sectionPos);
        int chunkZ = ChunkSectionPos.getZ(sectionPos);

        long key = toChunkKey(BlockPos.asLong(chunkX << 4, chunkY << 4, chunkZ << 4));

        BitSet set = this.pendingUpdatesByChunk.remove(key);

        if (set == null || set.isEmpty()) {
            return;
        }

        this.resetUpdateSetCache();

        int startX = chunkX << 4;
        int startY = chunkY << 4;
        int startZ = chunkZ << 4;

        set.stream().forEach(i -> {
            int x = (i >> 8) & 0xF;
            int y = (i >> 4) & 0xF;
            int z = i & 0xF;

            this.removePendingUpdate(BlockPos.asLong(startX + x, startY + y, startZ + z));
        });
    }


    @Override
    public void onPendingUpdateRemoved(long blockPos) {
        BitSet set = this.getUpdateSetFor(toChunkKey(blockPos));

        if (set != null) {
            set.clear(toLocalKey(blockPos));

            if (set.isEmpty()) {
                this.pendingUpdatesByChunk.remove(toChunkKey(blockPos));
            }
        }
    }

    @Override
    public void onPendingUpdateAdded(long blockPos) {
        BitSet set = this.getOrCreateUpdateSetFor(toChunkKey(blockPos));
        set.set(toLocalKey(blockPos));
    }

    private BitSet getUpdateSetFor(long chunkPos) {
        BitSet set = this.getCachedUpdateSet(chunkPos);

        if (set == null) {
            set = this.pendingUpdatesByChunk.get(chunkPos);

            if (set != null) {
                this.addUpdateSetToCache(chunkPos, set);
            }
        }

        return set;
    }

    private BitSet getOrCreateUpdateSetFor(long chunkPos) {
        BitSet set = this.getCachedUpdateSet(chunkPos);

        if (set == null) {
            set = this.pendingUpdatesByChunk.get(chunkPos);

            if (set == null) {
                this.pendingUpdatesByChunk.put(chunkPos, set = new BitSet(4096));
                this.addUpdateSetToCache(chunkPos, set);
            }
        }

        return set;
    }

    private BitSet getCachedUpdateSet(long chunkPos) {
        long[] lastChunkPos = this.lastChunkPos;

        for (int i = 0; i < lastChunkPos.length; i++) {
            if (lastChunkPos[i] == chunkPos) {
                return this.lastChunkUpdateSets[i];
            }
        }

        return null;
    }

    private void addUpdateSetToCache(long chunkPos, BitSet set) {
        long[] lastPos = this.lastChunkPos;
        lastPos[1] = lastPos[0];
        lastPos[0] = chunkPos;

        BitSet[] lastSet = this.lastChunkUpdateSets;
        lastSet[1] = lastSet[0];
        lastSet[0] = set;
    }

    protected void resetUpdateSetCache() {
        Arrays.fill(this.lastChunkPos, Long.MIN_VALUE);
        Arrays.fill(this.lastChunkUpdateSets, null);
    }

    private static long toChunkKey(long blockPos) {
        return blockPos & GLOBAL_TO_CHUNK_MASK;
    }

    private static int toLocalKey(long pos) {
        int x = BlockPos.unpackLongX(pos) & 0xF;
        int y = BlockPos.unpackLongY(pos) & 0xF;
        int z = BlockPos.unpackLongZ(pos) & 0xF;

        return x << 8 | y << 4 | z;
    }
}
