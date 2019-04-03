package me.jellysquid.mods.phosphor.common.mixins.lighting;

import me.jellysquid.mods.phosphor.common.world.lighting.LightingHooks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = Chunk.class)
public abstract class MixinChunk$Vanilla {
    private static final String SET_BLOCK_STATE_VANILLA = "setBlockState" +
            "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)" +
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
    @Group(name = "CreateSection", min = 1, max = 1)
    @Redirect(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "NEW",
                    args = "class=net/minecraft/world/chunk/storage/ExtendedBlockStorage"
            ),
            expect = 0
    )
    private ExtendedBlockStorage setBlockStateCreateSectionVanilla(int y, boolean storeSkylight) {
        return this.initSection(y, storeSkylight);
    }

    private ExtendedBlockStorage initSection(int y, boolean storeSkylight) {
        ExtendedBlockStorage storage = new ExtendedBlockStorage(y, storeSkylight);

        LightingHooks.initSkylightForSection(this.world, (Chunk) (Object) this, storage);

        return storage;
    }

    /**
     * Modifies the flag variable of setBlockState(BlockPos, IBlockState) to always be false after it is set.
     *
     * @author Angeline
     */
    @Group(name = "InjectGenerateSkylightMap", min = 1, max = 1)
    @ModifyVariable(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "STORE",
                    ordinal = 1
            ),
            index = 13,
            name = "flag",
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/world/chunk/Chunk;storageArrays:[Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;",
                            ordinal = 1
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;set(IIILnet/minecraft/block/state/IBlockState;)V"
                    )

            ),
            allow = 1
    )
    private boolean setBlockStateInjectGenerateSkylightMapVanilla(boolean generateSkylight) {
        return false;
    }

    /**
     * Modifies variable k1 before the conditional which decides to propagate skylight as to prevent it from
     * ever evaluating as true
     *
     * @author Angeline
     */
    @ModifyVariable(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "LOAD",
                    ordinal = 0
            ),
            index = 11,
            name = "k1",
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
     * Modifies variable j1 before the conditional which decides to propagate skylight as to prevent it from
     * ever evaluating as true.
     *
     * @author Angeline
     */
    @ModifyVariable(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "LOAD",
                    ordinal = 1
            ),
            index = 14,
            name = "j1",
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
