package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import me.jellysquid.mods.phosphor.common.chunk.level.LevelPropagatorExtended;
import me.jellysquid.mods.phosphor.common.chunk.level.LevelUpdateListener;
import me.jellysquid.mods.phosphor.common.util.collections.PendingLightUpdateQueue;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.light.LevelPropagator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelPropagator.class)
public abstract class MixinLevelPropagator implements LevelPropagatorExtended, LevelUpdateListener {
    private PendingLightUpdateQueue[] lightUpdateQueues;

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
    private int minPendingLevel;

    @Shadow
    private volatile boolean hasPendingUpdates;

    @Shadow
    protected abstract void propagateLevel(long id, int level, boolean decrease);

    @Shadow
    protected abstract int minLevel(int a, int b);

    @Shadow
    protected abstract void setLevel(long id, int level);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void reinit(int levelCount, int expectedLevelSize, int expectedTotalSize, CallbackInfo ci) {
        this.lightUpdateQueues = new PendingLightUpdateQueue[levelCount];

        for (int i = 0; i < this.lightUpdateQueues.length; i++) {
            this.lightUpdateQueues[i] = new PendingLightUpdateQueue();
        }
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue
     */
    @Overwrite
    private void increaseMinPendingLevel(int maxLevel) {
        int i = this.minPendingLevel;
        this.minPendingLevel = maxLevel;

        for (int j = i + 1; j < maxLevel; ++j) {
            if (!this.lightUpdateQueues[j].isEmpty()) {
                this.minPendingLevel = j;
                break;
            }
        }
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue
     */
    @Overwrite
    private void removePendingUpdate(long id, int level, int levelCount, boolean removeFully) {
        if (removeFully) {
            this.pendingUpdates.remove(id);
        }

        this.lightUpdateQueues[level].remove(id);

        if (this.lightUpdateQueues[level].isEmpty() && this.minPendingLevel == level) {
            this.increaseMinPendingLevel(levelCount);
        }

    }

    /**
     * @author JellySquid
     * @reason Use optimized queue
     */
    @Overwrite
    private void addPendingUpdate(long id, int level, int targetLevel) {
        this.pendingUpdates.put(id, (byte) level);
        this.lightUpdateQueues[targetLevel].add(id);

        if (this.minPendingLevel > targetLevel) {
            this.minPendingLevel = targetLevel;
        }

    }

    /**
     * @author JellySquid
     * @reason Use optimized queue
     */
    @Overwrite
    public final int applyPendingUpdates(int maxSteps) {
        if (this.minPendingLevel >= this.levelCount) {
            return maxSteps;
        }

        while (this.minPendingLevel < this.levelCount && maxSteps > 0) {
            --maxSteps;

            PendingLightUpdateQueue lightUpdateQueue = this.lightUpdateQueues[this.minPendingLevel];

            long next;

            while ((next = lightUpdateQueue.next()) != Long.MIN_VALUE) {
                int level = MathHelper.clamp(this.getLevel(next), 0, this.levelCount - 1);
                int pendingLevel = this.pendingUpdates.remove(next) & 255;

                if (pendingLevel < level) {
                    this.setLevel(next, pendingLevel);
                    this.propagateLevel(next, pendingLevel, true);
                } else if (pendingLevel > level) {
                    this.addPendingUpdate(next, pendingLevel, this.minLevel(this.levelCount - 1, pendingLevel));
                    this.setLevel(next, this.levelCount - 1);
                    this.propagateLevel(next, level, false);
                }
            }

            lightUpdateQueue.clear();

            this.increaseMinPendingLevel(this.levelCount);
        }

        this.hasPendingUpdates = this.minPendingLevel < this.levelCount;

        return maxSteps;
    }


    // [VanillaCopy] LevelPropagator#propagateLevel(long, long, int, boolean)
    @Override
    public void propagateLevel(long sourceId, BlockState sourceState, long targetId, int level, boolean decrease) {
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

    @Override
    public int getPropagatedLevel(long sourceId, BlockState sourceState, long targetId, int level) {
        return this.getPropagatedLevel(sourceId, targetId, level);
    }

    @Redirect(method = {"removePendingUpdate(JIIZ)V", "applyPendingUpdates"}, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteMap;remove(J)B", remap = false))
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

    @Override
    public void onPendingUpdateAdded(long key) {
        // NO-OP
    }

    @Override
    public void onPendingUpdateRemoved(long key) {
        // NO-OP
    }
}
