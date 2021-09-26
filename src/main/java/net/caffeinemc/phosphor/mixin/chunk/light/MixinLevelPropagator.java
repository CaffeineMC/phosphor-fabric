package net.caffeinemc.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.caffeinemc.phosphor.common.chunk.light.LevelPropagatorAccess;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.light.LevelPropagator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelPropagator.class)
public abstract class MixinLevelPropagator implements LevelPropagatorAccess {
    @Shadow
    @Final
    private Long2ByteMap pendingUpdates;

    @Shadow
    protected abstract int getLevel(long id);

    @Shadow
    @Final
    private int levelCount;

    @Shadow
    protected abstract int getPropagatedLevel(long sourceId, long targetId, int level);

    @Shadow
    protected abstract void updateLevel(long sourceId, long id, int level, int currentLevel, int pendingLevel, boolean decrease);

    @Shadow
    private volatile boolean hasPendingUpdates;

    @Shadow
    private int minPendingLevel;

    @Override
    @Invoker("propagateLevel")
    public abstract void invokePropagateLevel(long sourceId, long targetId, int level, boolean decrease);

    @Shadow
    protected abstract void propagateLevel(long sourceId, long targetId, int level, boolean decrease);

    @Shadow
    protected abstract void removePendingUpdate(long id);

    @Override
    public void propagateLevel(long sourceId, long targetId, boolean decrease) {
        this.propagateLevel(sourceId, targetId, this.getLevel(sourceId), decrease);
    }

    @Override
    public void checkForUpdates() {
        this.hasPendingUpdates = this.minPendingLevel < this.levelCount;
    }

    // [VanillaCopy] LevelPropagator#propagateLevel(long, long, int, boolean)
    /**
     * Mirrors {@link LevelPropagator#propagateLevel(long, int, boolean)}, but allows a block state to be passed to
     * prevent subsequent lookup later.
     */
    @Unique
    protected void propagateLevel(long sourceId, BlockState sourceState, long targetId, int level, boolean decrease) {
        int pendingLevel = this.pendingUpdates.get(targetId) & 0xFF;

        int propagatedLevel = this.getPropagatedLevel(sourceId, sourceState, targetId, level);
        int clampedLevel = MathHelper.clamp(propagatedLevel, 0, this.levelCount - 1);

        if (decrease) {
            this.updateLevel(sourceId, targetId, clampedLevel, this.getLevel(targetId), pendingLevel, true);

            return;
        }

        boolean flag;
        int resultLevel;

        if (pendingLevel == 0xFF) {
            flag = true;
            resultLevel = MathHelper.clamp(this.getLevel(targetId), 0, this.levelCount - 1);
        } else {
            resultLevel = pendingLevel;
            flag = false;
        }

        if (clampedLevel == resultLevel) {
            this.updateLevel(sourceId, targetId, this.levelCount - 1, flag ? resultLevel : this.getLevel(targetId), pendingLevel, false);
        }
    }

    /**
     * Copy of {@link #getPropagatedLevel(long, long, int)} but with an additional argument to pass the
     * block state belonging to {@param sourceId}.
     */
    @Unique
    protected int getPropagatedLevel(long sourceId, BlockState sourceState, long targetId, int level) {
        return this.getPropagatedLevel(sourceId, targetId, level);
    }

    @Redirect(method = { "removePendingUpdate(JIIZ)V", "applyPendingUpdates" }, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;remove(J)B", remap = false))
    private byte redirectRemovePendingUpdate(Long2ByteMap map, long key) {
        byte ret = map.remove(key);

        if (ret != map.defaultReturnValue()) {
            this.onPendingUpdateRemoved(key);
        }

        return ret;
    }

    @Redirect(method = "addPendingUpdate", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;put(JB)B", remap = false))
    private byte redirectAddPendingUpdate(Long2ByteMap map, long key, byte value) {
        byte ret = map.put(key, value);

        if (ret == map.defaultReturnValue()) {
            this.onPendingUpdateAdded(key);
        }

        return ret;
    }

    @Unique
    protected void onPendingUpdateAdded(long key) {
        // NO-OP
    }

    @Unique
    protected void onPendingUpdateRemoved(long key) {
        // NO-OP
    }
}
