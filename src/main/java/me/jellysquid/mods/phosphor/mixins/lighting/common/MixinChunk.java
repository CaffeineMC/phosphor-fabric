package me.jellysquid.mods.phosphor.mixins.lighting.common;

import me.jellysquid.mods.phosphor.api.IChunkLighting;
import me.jellysquid.mods.phosphor.api.IChunkLightingData;
import me.jellysquid.mods.phosphor.api.ILightingEngine;
import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import me.jellysquid.mods.phosphor.mod.world.WorldChunkSlice;
import me.jellysquid.mods.phosphor.mod.world.lighting.LightingHooks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnnecessaryQualifiedMemberReference")
@Mixin(value = Chunk.class)
public abstract class MixinChunk implements IChunkLighting, IChunkLightingData, ILightingEngineProvider {
    private static final EnumFacing[] HORIZONTAL = EnumFacing.Plane.HORIZONTAL.facings();

    @Shadow
    @Final
    private ExtendedBlockStorage[] storageArrays;

    @Shadow
    private boolean dirty;

    @Shadow
    @Final
    private int[] heightMap;

    @Shadow
    private int heightMapMinimum;

    @Shadow
    @Final
    private int[] precipitationHeightMap;

    @Shadow
    @Final
    private World world;

    @Shadow
    private boolean isTerrainPopulated;

    @Final
    @Shadow
    private boolean[] updateSkylightColumns;

    @Final
    @Shadow
    public int x;

    @Final
    @Shadow
    public int z;

    @Shadow
    private boolean isGapLightingUpdated;

    @Shadow
    public abstract TileEntity getTileEntity(BlockPos pos, Chunk.EnumCreateEntityType type);

    @Shadow
    public abstract IBlockState getBlockState(BlockPos pos);

    @Shadow
    protected abstract int getBlockLightOpacity(int x, int y, int z);

    @Shadow
    public abstract boolean canSeeSky(BlockPos pos);

    /**
     * Callback injected into the Chunk ctor to cache a reference to the lighting engine from the world.
     *
     * @author Angeline
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(CallbackInfo ci) {
        this.lightingEngine = ((ILightingEngineProvider) this.world).getLightingEngine();
    }

    /**
     * Callback injected to the head of getLightSubtracted(BlockPos, int) to force deferred light updates to be processed.
     *
     * @author Angeline
     */
    @Inject(method = "getLightSubtracted", at = @At("HEAD"))
    private void onGetLightSubtracted(BlockPos pos, int amount, CallbackInfoReturnable<Integer> cir) {
        this.lightingEngine.processLightUpdates(false);
    }

    /**
     * Callback injected at the end of onLoad() to have previously scheduled light updates scheduled again.
     *
     * @author Angeline
     */
    @Inject(method = "onLoad", at = @At("RETURN"))
    private void onLoad(CallbackInfo ci) {
        LightingHooks.scheduleRelightChecksForChunkBoundaries(this.world, (Chunk) (Object) this);
    }

    // === REPLACEMENTS ===

    /**
     * Replaces the call in setLightFor(Chunk, EnumSkyBlock, BlockPos) with our hook.
     *
     * @author Angeline
     */
    @Redirect(
            method = "setLightFor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;generateSkylightMap()V"
            ),
            expect = 0
    )
    private void setLightForRedirectGenerateSkylightMap(Chunk chunk, EnumSkyBlock type, BlockPos pos, int value) {
        LightingHooks.initSkylightForSection(this.world, (Chunk) (Object) this, this.storageArrays[pos.getY() >> 4]);
    }

    /**
     * @reason Overwrites relightBlock with a more efficient implementation.
     * @author Angeline
     */
    @Overwrite
    private void relightBlock(int x, int y, int z) {
        int i = this.heightMap[z << 4 | x] & 255;
        int j = i;

        if (y > i) {
            j = y;
        }

        while (j > 0 && this.getBlockLightOpacity(x, j - 1, z) == 0) {
            --j;
        }

        if (j != i) {
            this.heightMap[z << 4 | x] = j;

            if (this.world.provider.hasSkyLight()) {
                LightingHooks.relightSkylightColumn(this.world, (Chunk) (Object) this, x, z, i, j);
            }

            int l1 = this.heightMap[z << 4 | x];

            if (l1 < this.heightMapMinimum) {
                this.heightMapMinimum = l1;
            }
        }
    }

    /**
     * @reason Hook for calculating light updates only as needed. {@link MixinChunk#getCachedLightFor(EnumSkyBlock, BlockPos)} does not
     * call this hook.
     *
     * @author Angeline
     */
    @Overwrite
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        this.lightingEngine.processLightUpdatesForType(type, false);

        return this.getCachedLightFor(type, pos);
    }

    /**
     * @reason Hooks into checkLight() to check chunk lighting and returns immediately after, voiding the rest of the function.
     *
     * @author Angeline
     */
    @Overwrite
    public void checkLight() {
        this.isTerrainPopulated = true;

        LightingHooks.checkChunkLighting((Chunk) (Object) this, this.world);
    }

    /**
     * @reason Optimized version of recheckGaps. Avoids chunk fetches as much as possible.
     *
     * @author Angeline
     */
    @Overwrite
    private void recheckGaps(boolean onlyOne) {
        this.world.profiler.startSection("recheckGaps");

        WorldChunkSlice slice = new WorldChunkSlice(this.world, this.x, this.z);

        if (this.world.isAreaLoaded(new BlockPos(this.x * 16 + 8, 0, this.z * 16 + 8), 16)) {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    if (this.recheckGapsForColumn(slice, x, z)) {
                        if (onlyOne) {
                            this.world.profiler.endSection();

                            return;
                        }
                    }
                }
            }

            this.isGapLightingUpdated = false;
        }

        this.world.profiler.endSection();
    }

    private boolean recheckGapsForColumn(WorldChunkSlice slice, int x, int z) {
        int i = x + z * 16;

        if (this.updateSkylightColumns[i]) {
            this.updateSkylightColumns[i] = false;

            int height = this.getHeightValue(x, z);

            int x1 = this.x * 16 + x;
            int z1 = this.z * 16 + z;

            int max = this.recheckGapsGetLowestHeight(slice, x1, z1);

            this.recheckGapsSkylightNeighborHeight(slice, x1, z1, height, max);

            return true;
        }

        return false;
    }

    private int recheckGapsGetLowestHeight(WorldChunkSlice slice, int x, int z) {
        int max = Integer.MAX_VALUE;

        for (EnumFacing facing : HORIZONTAL) {
            int j = x + facing.getXOffset();
            int k = z + facing.getZOffset();

            max = Math.min(max, slice.getChunkFromWorldCoords(j, k).getLowestHeight());
        }

        return max;
    }

    private void recheckGapsSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int height, int max) {
        this.checkSkylightNeighborHeight(slice, x, z, max);

        for (EnumFacing facing : HORIZONTAL) {
            int j = x + facing.getXOffset();
            int k = z + facing.getZOffset();

            this.checkSkylightNeighborHeight(slice, j, k, height);
        }
    }

    private void checkSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int maxValue) {
        int i = slice.getChunkFromWorldCoords(x, z).getHeightValue(x & 15, z & 15);

        if (i > maxValue) {
            this.updateSkylightNeighborHeight(slice, x, z, maxValue, i + 1);
        } else if (i < maxValue) {
            this.updateSkylightNeighborHeight(slice, x, z, i, maxValue + 1);
        }
    }

    private void updateSkylightNeighborHeight(WorldChunkSlice slice, int x, int z, int startY, int endY) {
        if (endY > startY) {
            if (!slice.isLoaded(x, z, 16)) {
                return;
            }

            for (int i = startY; i < endY; ++i) {
                this.world.checkLightFor(EnumSkyBlock.SKY, new BlockPos(x, i, z));
            }

            this.dirty = true;
        }
    }

    @Shadow
    public abstract int getHeightValue(int i, int j);

    // === INTERFACE IMPL ===

    private short[] neighborLightChecks;

    private boolean isLightInitialized;

    private ILightingEngine lightingEngine;

    @Override
    public short[] getNeighborLightChecks() {
        return this.neighborLightChecks;
    }

    @Override
    public void setNeighborLightChecks(short[] data) {
        this.neighborLightChecks = data;
    }

    @Override
    public ILightingEngine getLightingEngine() {
        return this.lightingEngine;
    }

    @Override
    public boolean isLightInitialized() {
        return this.isLightInitialized;
    }

    @Override
    public void setLightInitialized(boolean lightInitialized) {
        this.isLightInitialized = lightInitialized;
    }

    @Shadow
    protected abstract void setSkylightUpdated();

    @Override
    public void setSkylightUpdatedPublic() {
        this.setSkylightUpdated();
    }

    @Override
    public int getCachedLightFor(EnumSkyBlock type, BlockPos pos) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;

        ExtendedBlockStorage extendedblockstorage = this.storageArrays[j >> 4];

        if (extendedblockstorage == Chunk.NULL_BLOCK_STORAGE) {
            if (this.canSeeSky(pos)) {
                return type.defaultLightValue;
            } else {
                return 0;
            }
        } else if (type == EnumSkyBlock.SKY) {
            if (!this.world.provider.hasSkyLight()) {
                return 0;
            } else {
                return extendedblockstorage.getSkyLight(i, j & 15, k);
            }
        } else {
            if (type == EnumSkyBlock.BLOCK) {
                return extendedblockstorage.getBlockLight(i, j & 15, k);
            } else {
                return type.defaultLightValue;
            }
        }
    }


    // === END OF INTERFACE IMPL ===
}
