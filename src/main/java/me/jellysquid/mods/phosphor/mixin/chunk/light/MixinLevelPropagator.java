package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedLevelPropagator;
import me.jellysquid.mods.phosphor.common.util.collections.PendingLevelUpdateTracker;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.light.LevelPropagator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelPropagator.class)
public abstract class MixinLevelPropagator implements ExtendedLevelPropagator {
    @Shadow
    private int minPendingLevel;

    @Shadow
    @Final
    private Long2ByteMap pendingUpdates;

    @Shadow
    protected abstract int getLevel(long id);

    @Shadow
    protected abstract int minLevel(int a, int b);

    @Shadow
    @Final
    private int levelCount;

    @Shadow
    private volatile boolean hasPendingUpdates;

    @Shadow
    protected abstract void setLevel(long id, int level);

    @Shadow
    protected abstract void propagateLevel(long id, int targetLevel, boolean mergeAsMin);

    @Shadow
    protected abstract int getPropagatedLevel(long sourceId, long targetId, int level);

    @Shadow
    protected abstract void updateLevel(long sourceId, long id, int level, int currentLevel, int pendingLevel, boolean decrease);

    private PendingLevelUpdateTracker[] pendingUpdateSet;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(int levelCount, int initLevelCapacity, int initTotalCapacity, CallbackInfo ci) {
        this.pendingUpdateSet = new PendingLevelUpdateTracker[levelCount];

        for (int i = 0; i < levelCount; i++) {
            this.pendingUpdateSet[i] = new PendingLevelUpdateTracker(initLevelCapacity, 4096);
        }
    }

    /**
     * @reason Redirect to our custom update tracker
     * @author JellySquid
     */
    @Overwrite
    private void increaseMinPendingLevel(int min) {
        int prevMin = this.minPendingLevel;
        this.minPendingLevel = min;

        for (int level = prevMin + 1; level < min; ++level) {
            if (!(this.pendingUpdateSet[level].queueReadIdx >= this.pendingUpdateSet[level].queueWriteIdx)) {
                this.minPendingLevel = level;

                break;
            }
        }

    }

    /**
     * @reason Redirect to our custom update tracker
     * @author JellySquid
     */
    @Overwrite
    public void removePendingUpdate(long id) {
        int level = this.pendingUpdates.get(id) & 255;

        if (level != 255) {
            int prevLevel = this.getLevel(id);
            int nextLevel = this.minLevel(prevLevel, level);

            this.removePendingUpdate(id, nextLevel, this.levelCount, true);

            this.hasPendingUpdates = this.minPendingLevel < this.levelCount;
        }
    }

    /**
     * @reason Redirect to our custom update tracker
     * @author JellySquid
     */
    @Overwrite
    private void removePendingUpdate(long id, int level, int maxLevel, boolean removeFromLevelMap) {
        if (removeFromLevelMap) {
            this.pendingUpdates.remove(id);
        }

        this.pendingUpdateSet[level].remove(id);

        if (this.pendingUpdateSet[level].queueReadIdx >= this.pendingUpdateSet[level].queueWriteIdx && this.minPendingLevel == level) {
            this.increaseMinPendingLevel(maxLevel);
        }
    }

    /**
     * @reason Redirect to our custom update tracker
     * @author JellySquid
     */
    @Overwrite
    private void addPendingUpdate(long id, int level, int targetLevel) {
        this.pendingUpdates.put(id, (byte) level);

        this.pendingUpdateSet[targetLevel].add(id);

        if (this.minPendingLevel > targetLevel) {
            this.minPendingLevel = targetLevel;
        }
    }

    /**
     * @reason Redirect to our custom update tracker
     * @author JellySquid
     */
    @Overwrite
    public final int applyPendingUpdates(int maxSteps) {
        if (this.minPendingLevel >= this.levelCount) {
            return maxSteps;
        }

        while (this.minPendingLevel < this.levelCount && maxSteps > 0) {
            PendingLevelUpdateTracker set = this.pendingUpdateSet[this.minPendingLevel];

            int qIdx = set.queueReadIdx;

            while (qIdx < set.queueWriteIdx && maxSteps > 0) {
                maxSteps--;

                long pos = set.queue[qIdx++];

                if (pos == Integer.MIN_VALUE) {
                    continue;
                }

                boolean skip = !set.consume(pos, qIdx - 1);

                if (skip) {
                    continue;
                }

                int from = MathHelper.clamp(this.getLevel(pos), 0, this.levelCount - 1);
                int to = this.pendingUpdates.remove(pos) & 255;

                if (to < from) {
                    this.setLevel(pos, to);
                    this.propagateLevel(pos, to, true);
                } else if (to > from) {
                    this.addPendingUpdate(pos, to, this.minLevel(this.levelCount - 1, to));

                    this.setLevel(pos, this.levelCount - 1);
                    this.propagateLevel(pos, from, false);
                }
            }

            set.queueReadIdx = qIdx;

            if (set.queueReadIdx >= set.queueWriteIdx) {
                this.increaseMinPendingLevel(this.levelCount);

                set.clear();
            }
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
}
