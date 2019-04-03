package me.jellysquid.mods.phosphor.common.mixins.lighting;

import me.jellysquid.mods.phosphor.common.world.lighting.LightingHooks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = Chunk.class)
public abstract class MixinChunk$Vanilla {
    private static final String SET_BLOCK_STATE_VANILLA = "Lnet/minecraft/world/chunk/Chunk;setBlockState" +
            "(Lnet/minecraft/util/math/BlockPos;" +
            "Lnet/minecraft/block/state/IBlockState;)" +
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
     * Modifies the flag variable of setBlockState(BlockPos, IBlockState) before conditionals.
     *
     * @author Angeline
     */
    @Group(name = "InjectGenerateSkylightMap", min = 1, max = 1)
    @ModifyVariable(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "JUMP",
                    opcode = Opcodes.IFEQ
            ),
            index = 13,
            name = "flag",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/block/state/IBlockState;getBlock()Lnet/minecraft/block/Block;",
                            ordinal = 2
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
     * Redirects the getLightFor function in the else branch of setBlockState(BlockPos, IBlockState) to always return -1.
     * This causes the if statement to never evaluate to true and effectively nullifies the call. This should probably be replaced
     * with a better alternative.
     */
    @Group(name = "GetLightFor", min = 2, max = 2)
    @Redirect(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;getLightFor(Lnet/minecraft/world/EnumSkyBlock;Lnet/minecraft/util/math/BlockPos;)I"
            ),
            expect = 0
    )
    private int setBlockStateVoidGetLightForVanilla(Chunk chunk, EnumSkyBlock type, BlockPos pos) {
        return -1;
    }

    /**
     * Redirects the propagateSkylightOcclusion call we were trying to avoid with the
     * setBlockStateVoidGetLightForVanilla hack.
     * <p>
     * If for some reason the statement still evaluates and the method is called, nothing will happen. This is a fail-safe
     * if the aforementioned hack does not work. We do NOT want to spend time in getLightFor methods in the conditional
     * if we can avoid it.
     *
     * @author Angeline
     */
    @Group(name = "PropagateSkylightOcclusion", min = 1, max = 1)
    @Redirect(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;propagateSkylightOcclusion(II)V"
            ),
            expect = 0
    )
    private void setBlockStateVoidPropagateSkylightOcclusionVanilla(Chunk chunk, int x, int z) {
        // NO-OP!
        // We don't want to do any work here.
    }
}
