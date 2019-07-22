package me.jellysquid.mods.phosphor.mixins.lighting.common;


import me.jellysquid.mods.phosphor.mod.world.lighting.LightingHooks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = Chunk.class, priority = 10055)
public abstract class MixinChunk$Sponge {
    private static final String SET_BLOCK_STATE_SPONGE = "bridge$setBlockState" +
            "(Lnet/minecraft/util/math/BlockPos;" +
            "Lnet/minecraft/block/state/IBlockState;" +
            "Lnet/minecraft/block/state/IBlockState;" +
            "Lorg/spongepowered/api/world/BlockChangeFlag;)" +
            "Lnet/minecraft/block/state/IBlockState;";

    @Shadow
    @Final
    private World world;

    /**
     * Redirects the construction of the ExtendedBlockStorage in setBlockState(BlockPos, IBlockState). We need to initialize
     * the skylight data for the constructed section as soon as possible.
     *
     * @author Angeline
     */
    @Dynamic
    @Redirect(
            method = SET_BLOCK_STATE_SPONGE,
            at = @At(
                    value = "NEW",
                    args = "class=net/minecraft/world/chunk/storage/ExtendedBlockStorage"
            ),
            expect = 0
    )
    private ExtendedBlockStorage setBlockStateCreateSectionSponge(int y, boolean storeSkylight) {
        return this.initSection(y, storeSkylight);
    }

    private ExtendedBlockStorage initSection(int y, boolean storeSkylight) {
        ExtendedBlockStorage storage = new ExtendedBlockStorage(y, storeSkylight);

        LightingHooks.initSkylightForSection(this.world, (Chunk) (Object) this, storage);

        return storage;
    }

    /**
     * Modifies variable requiresNewLightCalculations before it is used in the conditional that decides whether or not
     * generateSkylightMap() should be called. We want it to always take the else branch.
     *
     * @author Angeline
     */
    @Dynamic
    @ModifyVariable(
            method = SET_BLOCK_STATE_SPONGE,
            at = @At(
                    value = "LOAD",
                    ordinal = 0
            ),
            index = 14,
            name = "requiresNewLightCalculations",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;get(III)Lnet/minecraft/block/state/IBlockState;"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/Chunk;generateSkylightMap()V"
                    )
            ),
            allow = 1
    )
    private boolean setBlockStateInjectGenerateSkylightMapVanilla(boolean generateSkylight) {
        return false;
    }

    /**
     * Modifies variable newBlockLightOpacity to match postNewBlockLightOpacity before the conditional which decides to
     * propagate skylight as to prevent it from ever evaluating as true.
     *
     * @author Angeline
     */
    @Dynamic
    @ModifyVariable(
            method = SET_BLOCK_STATE_SPONGE,
            at = @At(
                    value = "LOAD",
                    ordinal = 1
            ),
            index = 13,
            name = "newBlockLightOpacity",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/Chunk;relightBlock(III)V",
                            ordinal = 1
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/Chunk;propagateSkylightOcclusion(II)V"
                    )

            ),
            allow = 1
    )
    private int setBlockStatePreventPropagateSkylightOcclusion1(int generateSkylight) {
        return WIZARD_MAGIC;
    }

    /**
     * Modifies variable postNewBlockLightOpacity to match newBlockLightOpacity before the conditional which decides to
     * propagate skylight as to prevent it from ever evaluating as true.
     *
     * @author Angeline
     */
    @Dynamic
    @ModifyVariable(
            method = SET_BLOCK_STATE_SPONGE,
            at = @At(
                    value = "LOAD",
                    ordinal = 0
            ),
            index = 24,
            name = "postNewBlockLightOpacity",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/Chunk;relightBlock(III)V",
                            ordinal = 1
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/Chunk;propagateSkylightOcclusion(II)V"
                    )

            ),
            allow = 1
    )
    private int setBlockStatePreventPropagateSkylightOcclusion2(int generateSkylight) {
        return WIZARD_MAGIC;
    }

    private static final int WIZARD_MAGIC = 694698818;

}
