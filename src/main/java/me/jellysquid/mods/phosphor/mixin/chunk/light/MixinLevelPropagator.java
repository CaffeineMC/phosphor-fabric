package me.jellysquid.mods.phosphor.mixin.chunk.light;

import me.jellysquid.mods.phosphor.common.chunk.level.LevelPropagatorExtended;
import me.jellysquid.mods.phosphor.common.chunk.level.LevelUpdateListener;
import me.jellysquid.mods.phosphor.common.util.collections.LevelUpdateManager;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
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
public abstract class MixinLevelPropagator implements LevelPropagatorExtended, LevelUpdateListener {
    private int maxLevel;

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

    private LevelUpdateManager levelUpdateManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void reinit(int levelCount, int expectedLevelSize, int expectedTotalSize, CallbackInfo ci) {
        this.levelUpdateManager = new LevelUpdateManager(levelCount);
        this.maxLevel = levelCount - 1;
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue
     */
    @Overwrite
    private void increaseMinPendingLevel(int maxLevel) {
        int minLevel = this.minPendingLevel;

        this.minPendingLevel = maxLevel;

        for (int level = minLevel + 1; level < maxLevel; level++) {
            if (this.levelUpdateManager.isQueueEmpty(level)) {
                continue;
            }

            this.minPendingLevel = level;

            break;
        }
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue
     */
    @Overwrite
    public void removePendingUpdate(long id) {
        int pendingUpdate = this.levelUpdateManager.getPendingUpdate(id);

        if (pendingUpdate != 255) {
            int level = this.getLevel(id);
            int min = this.minLevel(level, pendingUpdate);

            this.removePendingUpdate(id, min, this.levelCount, true);

            this.hasPendingUpdates = this.minPendingLevel < this.levelCount;
        }
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue, track pending updates by section
     */
    @Overwrite
    private void removePendingUpdate(long id, int level, int levelCount, boolean removeFully) {
        if (removeFully) {
            if (this.levelUpdateManager.removeAndDeque(id, level)) {
                this.onPendingUpdateRemoved(id);
            }
        } else {
            if (this.levelUpdateManager.dequeue(id, level)) {
                this.onPendingUpdateRemoved(id);
            }
        }

        if (this.minPendingLevel == level && this.levelUpdateManager.isQueueEmpty(level)) {
            this.increaseMinPendingLevel(levelCount);
        }
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue, track pending updates by section
     */
    @Overwrite
    private void addPendingUpdate(long id, int level, int targetLevel) {
        if (this.levelUpdateManager.enqueue(id, level, targetLevel)) {
            this.onPendingUpdateAdded(id);
        }

        if (this.minPendingLevel > targetLevel) {
            this.minPendingLevel = targetLevel;
        }
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue
     */
    @Overwrite
    public void updateLevel(long sourceId, long id, int level, boolean decrease) {
        this.updateLevel(sourceId, id, level, this.getLevel(id), this.levelUpdateManager.getPendingUpdate(id), decrease);

        this.hasPendingUpdates = this.minPendingLevel < this.levelCount;
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue, track pending updates by section
     */
    @Overwrite
    public final int applyPendingUpdates(int maxSteps) {
        if (this.minPendingLevel >= this.levelCount) {
            return maxSteps;
        }

        while (this.minPendingLevel < this.levelCount && maxSteps > 0) {
            --maxSteps;

            long next;

            while ((next = this.levelUpdateManager.next(this.minPendingLevel)) != Long.MIN_VALUE) {
                int level = MathHelper.clamp(this.getLevel(next), 0, this.maxLevel);
                int pendingLevel = this.levelUpdateManager.removePendingUpdate(next);

                if (pendingLevel == 255) {
                    this.onPendingUpdateRemoved(next);
                }

                if (pendingLevel < level) {
                    this.setLevel(next, pendingLevel);
                    this.propagateLevel(next, pendingLevel, true);
                } else if (pendingLevel > level) {
                    this.addPendingUpdate(next, pendingLevel, this.minLevel(this.maxLevel, pendingLevel));
                    this.setLevel(next, this.maxLevel);
                    this.propagateLevel(next, level, false);
                }
            }

            this.increaseMinPendingLevel(this.levelCount);
        }

        if (maxSteps > 0) {
            this.levelUpdateManager.clear();
        }

        this.hasPendingUpdates = this.minPendingLevel < this.levelCount;

        return maxSteps;
    }


    // [VanillaCopy] LevelPropagator#propagateLevel(long, long, int, boolean)
    @Override
    public void propagateLevel(long sourceId, BlockState sourceState, long targetId, int level, boolean decrease, Direction dir) {
        int pendingLevel = this.levelUpdateManager.getPendingUpdate(targetId);

        int propagatedLevel = this.getPropagatedLevel(sourceId, sourceState, targetId, level, null);
        int clampedLevel = MathHelper.clamp(propagatedLevel, 0, this.maxLevel);

        if (decrease) {
            this.updateLevel(sourceId, targetId, clampedLevel, this.getLevel(targetId), pendingLevel, true);

            return;
        }

        boolean flag;
        int resultLevel;

        if (pendingLevel == 0xFF) {
            flag = true;
            resultLevel = MathHelper.clamp(this.getLevel(targetId), 0, this.maxLevel);
        } else {
            resultLevel = pendingLevel;
            flag = false;
        }

        if (clampedLevel == resultLevel) {
            if (flag) {
                this.updateLevel(sourceId, targetId, this.maxLevel, resultLevel, pendingLevel, false);
            } else {
                this.updateLevel(sourceId, targetId, this.maxLevel, this.getLevel(targetId), pendingLevel, false);
            }
        }
    }

    /**
     * @author JellySquid
     * @reason Use optimized queue
     */
    @Overwrite
    public final void propagateLevel(long sourceId, long targetId, int level, boolean decrease) {
        int i = this.levelUpdateManager.getPendingUpdate(targetId);
        int j = MathHelper.clamp(this.getPropagatedLevel(sourceId, targetId, level), 0, this.maxLevel);

        if (decrease) {
            this.updateLevel(sourceId, targetId, j, this.getLevel(targetId), i, true);
        } else {
            int l;
            boolean bl2;

            if (i == 255) {
                bl2 = true;
                l = MathHelper.clamp(this.getLevel(targetId), 0, this.maxLevel);
            } else {
                l = i;
                bl2 = false;
            }

            if (j == l) {
                this.updateLevel(sourceId, targetId, this.maxLevel, bl2 ? l : this.getLevel(targetId), i, false);
            }
        }
    }

    /**
     * @author JellySquid
     */
    @Overwrite
    public int getPendingUpdateCount() {
        return this.levelUpdateManager.getPendingUpdateCount();
    }

    @Override
    public int getPropagatedLevel(long sourceId, BlockState sourceState, long targetId, int level, Direction dir) {
        return this.getPropagatedLevel(sourceId, targetId, level);
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
