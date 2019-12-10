package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.Long2ByteFunction;
import me.jellysquid.mods.phosphor.common.util.PendingLevelUpdateTracker;
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
public abstract class MixinLevelPropagator  {
    @Shadow
    private int minLevel;

    @Shadow
    @Final
    private Long2ByteFunction idToLevel;

    @Shadow
    protected abstract int getLevel(long id);

    @Shadow
    protected abstract int min(int a, int b);

    @Shadow
    @Final
    private int levelCount;

    @Shadow
    private volatile boolean hasUpdates;


    @Shadow
    protected abstract void setLevel(long id, int level);

    @Shadow
    protected abstract void updateNeighborsRecursively(long id, int targetLevel, boolean mergeAsMin);

    private PendingLevelUpdateTracker[] pendingUpdateSet;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(int levelCount, int initLevelCapacity, int initTotalCapacity, CallbackInfo ci) {
        this.pendingUpdateSet = new PendingLevelUpdateTracker[levelCount];

        for (int i = 0; i < levelCount; i++) {
            this.pendingUpdateSet[i] = new PendingLevelUpdateTracker(initLevelCapacity, 4096);
        }
    }

    /**
     * @author JellySquid
     */
    @Overwrite
    public void updateMinLevel(int min) {
        int prevMin = this.minLevel;
        this.minLevel = min;

        for (int level = prevMin + 1; level < min; ++level) {
            if (!this.pendingUpdateSet[level].isEmpty()) {
                this.minLevel = level;

                break;
            }
        }

    }

    /**
     * @author JellySquid
     */
    @Overwrite
    public void remove(long id) {
        int level = this.idToLevel.get(id) & 255;

        if (level != 255) {
            int prevLevel = this.getLevel(id);
            int nextLevel = this.min(prevLevel, level);

            this.removeFromLevel(id, nextLevel, this.levelCount, true);

            this.hasUpdates = this.minLevel < this.levelCount;
        }
    }

    /**
     * @author JellySquid
     */
    @Overwrite
    private void removeFromLevel(long id, int level, int maxLevel, boolean removeFromLevelMap) {
        if (removeFromLevelMap) {
            this.idToLevel.remove(id);
        }

        this.pendingUpdateSet[level].remove(id);

        if (this.pendingUpdateSet[level].isEmpty() && this.minLevel == level) {
            this.updateMinLevel(maxLevel);
        }
    }

    /**
     * @author JellySquid
     */
    @Overwrite
    private void add(long id, int level, int targetLevel) {
        this.idToLevel.put(id, (byte) level);

        this.pendingUpdateSet[targetLevel].add(id);

        if (this.minLevel > targetLevel) {
            this.minLevel = targetLevel;
        }
    }

    /**
     * @author JellySquid
     */
    @Overwrite
    public final int updateAllRecursively(int maxSteps) {
        if (this.minLevel >= this.levelCount) {
            return maxSteps;
        }

        while (this.minLevel < this.levelCount && maxSteps > 0) {
            PendingLevelUpdateTracker set = this.pendingUpdateSet[this.minLevel];

            while (set.queueReadIdx < set.queueWriteIdx && maxSteps > 0) {
                maxSteps--;

                long pos = set.queue[set.queueReadIdx];

                if (pos == Integer.MIN_VALUE) {
                    set.queueReadIdx++;

                    continue;
                }

                boolean skip = !set.consume(pos, set.queueReadIdx);

                if (set.isEmpty()) {
                    this.updateMinLevel(this.levelCount);
                }

                if (skip) {
                    set.queueReadIdx++;

                    continue;
                }

                int from = MathHelper.clamp(this.getLevel(pos), 0, this.levelCount - 1);
                int to = this.idToLevel.remove(pos) & 255;

                if (to < from) {
                    this.setLevel(pos, to);
                    this.updateNeighborsRecursively(pos, to, true);
                } else if (to > from) {
                    this.add(pos, to, this.min(this.levelCount - 1, to));
                    this.setLevel(pos, this.levelCount - 1);
                    this.updateNeighborsRecursively(pos, from, false);
                }

                set.queueReadIdx++;
            }

            if (set.isEmpty()) {
                set.clear();
            }
        }

        this.hasUpdates = this.minLevel < this.levelCount;

        return maxSteps;
    }
}
