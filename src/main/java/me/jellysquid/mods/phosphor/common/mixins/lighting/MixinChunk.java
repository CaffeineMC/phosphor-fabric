package me.jellysquid.mods.phosphor.common.mixins.lighting;

import me.jellysquid.mods.phosphor.api.IChunkLighting;
import me.jellysquid.mods.phosphor.api.IChunkLightingData;
import me.jellysquid.mods.phosphor.api.ILightingEngine;
import me.jellysquid.mods.phosphor.api.ILightingEngineProvider;
import me.jellysquid.mods.phosphor.common.world.LightingHooks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(Chunk.class)
public abstract class MixinChunk implements IChunkLighting, IChunkLightingData, ILightingEngineProvider {
    // === START OF SHADOWS ===

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
    public int x;

    @Shadow
    @Final
    public int z;

    @Shadow
    @Final
    private int[] precipitationHeightMap;

    @Shadow
    @Final
    private World world;

    @Shadow
    private boolean isTerrainPopulated;

    @Shadow
    public abstract TileEntity shadow$getTileEntity(BlockPos pos, Chunk.EnumCreateEntityType type);

    @Shadow
    public abstract IBlockState shadow$getBlockState(BlockPos pos);

    @Shadow
    protected abstract int getBlockLightOpacity(int x, int y, int z);

    @Shadow
    public abstract boolean canSeeSky(BlockPos pos);

    // === END OF SHADOWS ===

    // === HOOKS ===

    @Inject(method = "getLightSubtracted", at = @At("HEAD"))
    private void onGetLightSubtracted(BlockPos pos, int amount, CallbackInfoReturnable<Integer> cir) {
        this.lightingEngine.procLightUpdates();
    }

    @Inject(method = "onLoad", at = @At("RETURN"))
    private void onLoad(CallbackInfo ci) {
        LightingHooks.scheduleRelightChecksForChunkBoundaries(this.world, (Chunk) (Object) this);
    }

    // === END OF HOOKS ===

    // === REPLACEMENTS ===

    /**
     * @author Angeline
     */
    @Overwrite
    public void setLightFor(EnumSkyBlock type, BlockPos pos, int value) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;
        ExtendedBlockStorage extendedblockstorage = this.storageArrays[j >> 4];

        if (extendedblockstorage == Chunk.NULL_BLOCK_STORAGE) {
            extendedblockstorage = new ExtendedBlockStorage(j >> 4 << 4, this.world.provider.hasSkyLight());
            this.storageArrays[j >> 4] = extendedblockstorage;

            LightingHooks.initSkylightForSection(this.world, (Chunk) (Object) this, extendedblockstorage);
        }

        this.dirty = true;

        if (type == EnumSkyBlock.SKY) {
            if (this.world.provider.hasSkyLight()) {
                extendedblockstorage.setSkyLight(i, j & 15, k, value);
            }
        } else if (type == EnumSkyBlock.BLOCK) {
            extendedblockstorage.setBlockLight(i, j & 15, k, value);
        }
    }

    /**
     * @author Angeline
     */
    @Nullable
    @Overwrite
    public IBlockState setBlockState(BlockPos pos, IBlockState state) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;
        int l = k << 4 | i;

        if (j >= this.precipitationHeightMap[l] - 1) {
            this.precipitationHeightMap[l] = -999;
        }

        int i1 = this.heightMap[l];
        IBlockState iblockstate = this.shadow$getBlockState(pos);

        if (iblockstate == state) {
            return null;
        } else {
            Block block = state.getBlock();
            Block block1 = iblockstate.getBlock();
            ExtendedBlockStorage extendedblockstorage = this.storageArrays[j >> 4];

            if (extendedblockstorage == Chunk.NULL_BLOCK_STORAGE) {
                if (block == Blocks.AIR) {
                    return null;
                }

                extendedblockstorage = new ExtendedBlockStorage(j >> 4 << 4, this.world.provider.hasSkyLight());
                this.storageArrays[j >> 4] = extendedblockstorage;
                LightingHooks.initSkylightForSection(this.world, (Chunk) (Object) this, extendedblockstorage);
            }

            extendedblockstorage.set(i, j & 15, k, state);

            //if (block1 != block)
            {
                if (!this.world.isRemote) {
                    if (block1 != block) //Only fire block breaks when the block changes.
                    {
                        block1.breakBlock(this.world, pos, iblockstate);
                    }
                    TileEntity te = this.shadow$getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
                    if (te != null && te.shouldRefresh(this.world, pos, iblockstate, state)) {
                        this.world.removeTileEntity(pos);
                    }
                } else if (block1.hasTileEntity(iblockstate)) {
                    TileEntity te = this.shadow$getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
                    if (te != null && te.shouldRefresh(this.world, pos, iblockstate, state)) {
                        this.world.removeTileEntity(pos);
                    }
                }
            }

            if (extendedblockstorage.get(i, j & 15, k).getBlock() != block) {
                return null;
            } else {
                int j1 = state.getLightOpacity(this.world, pos);

                if (j1 > 0) {
                    if (j >= i1) {
                        this.relightBlock(i, j + 1, k);
                    }
                } else if (j == i1 - 1) {
                    this.relightBlock(i, j, k);
                }

                // If capturing blocks, only run block physics for TE's. Non-TE's are handled in ForgeHooks.onPlaceItemIntoWorld
                if (!this.world.isRemote && block1 != block && (!this.world.captureBlockSnapshots || block.hasTileEntity(state))) {
                    block.onBlockAdded(this.world, pos, state);
                }

                if (block.hasTileEntity(state)) {
                    TileEntity tileentity1 = this.shadow$getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);

                    if (tileentity1 == null) {
                        tileentity1 = block.createTileEntity(this.world, state);
                        this.world.setTileEntity(pos, tileentity1);
                    }

                    if (tileentity1 != null) {
                        tileentity1.updateContainingBlockInfo();
                    }
                }

                this.dirty = true;
                return iblockstate;
            }
        }
    }

    /**
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

    @Shadow
    public abstract IBlockState getBlockState(int x, int y, int z);

    /**
     * Hook for calculating light updates only as needed. {@link MixinChunk#getCachedLightFor(EnumSkyBlock, BlockPos)} does not
     * call this hook.
     *
     * @author Angeline
     */
    @Overwrite
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        this.lightingEngine.procLightUpdates(type);

        return this.getCachedLightFor(type, pos);
    }

    /**
     * @author Angeline
     */
    @Overwrite
    public void checkLight() {
        this.isTerrainPopulated = true;

        LightingHooks.checkChunkLighting((Chunk) (Object) this, this.world);
    }

    @Override
    public int getCachedLightFor(EnumSkyBlock type, BlockPos pos) {
        int i = pos.getX() & 15;
        int j = pos.getY();
        int k = pos.getZ() & 15;

        ExtendedBlockStorage extendedblockstorage = this.storageArrays[j >> 4];

        if (extendedblockstorage == Chunk.NULL_BLOCK_STORAGE) {
            return this.canSeeSky(pos) ? type.defaultLightValue : 0;
        } else if (type == EnumSkyBlock.SKY) {
            return !this.world.provider.hasSkyLight() ? 0 : extendedblockstorage.getSkyLight(i, j & 15, k);
        } else {
            return type == EnumSkyBlock.BLOCK ? extendedblockstorage.getBlockLight(i, j & 15, k) : type.defaultLightValue;
        }
    }

    /**
     * @author
     */
    @Overwrite
    public void generateSkylightMap() {
        int maxY = this.getTopFilledSegment();

        this.heightMapMinimum = Integer.MAX_VALUE;

        BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                this.precipitationHeightMap[x + (z << 4)] = -999;

                for (int y = maxY + 16; y > 0; --y) {
                    if (this.getBlockState(x, y - 1, z).getLightOpacity() != 0) {
                        this.heightMap[z << 4 | x] = y;

                        if (y < this.heightMapMinimum) {
                            this.heightMapMinimum = y;
                        }

                        break;
                    }
                }

                if (this.world.provider.hasSkyLight()) {
                    int light = 15;
                    int y2 = maxY + 16 - 1;

                    do {
                        int opacity = this.getBlockState(x, y2, z).getLightOpacity();

                        if (opacity == 0 && light != 15) {
                            opacity = 1;
                        }

                        light -= opacity;

                        if (light > 0) {
                            ExtendedBlockStorage extendedblockstorage = this.storageArrays[y2 >> 4];

                            if (extendedblockstorage != Chunk.NULL_BLOCK_STORAGE) {
                                extendedblockstorage.setSkyLight(x, y2 & 15, z, light);

                                if (this.world.isRemote) {
                                    this.world.notifyLightSet(pos.setPos((this.x << 4) + x, y2, (this.z << 4) + z));
                                }
                            }
                        }

                        --y2;

                    }
                    while (y2 > 0 && light > 0);
                }
            }
        }


        pos.release();

        this.dirty = true;
    }

    @Shadow
    public abstract int getTopFilledSegment();

    // === END OF REPLACEMENTS ===

    // === INTERFACE IMPL ===

    private short[] neighborLightChecks;

    private ILightingEngine lightingEngine;

    private boolean isLightInitialized;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(CallbackInfo ci) {
        this.lightingEngine = ((ILightingEngineProvider) this.world).getLightingEngine();
    }

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

    // === END OF INTERFACE IMPL ===
}
