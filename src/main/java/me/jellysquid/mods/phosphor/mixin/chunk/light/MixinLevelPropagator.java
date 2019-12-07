package me.jellysquid.mods.phosphor.mixin.chunk.light;

import it.unimi.dsi.fastutil.longs.*;
import me.jellysquid.mods.phosphor.common.chunk.ExtendedLevelPropagator;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.light.LevelPropagator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelPropagator.class)
public abstract class MixinLevelPropagator implements ExtendedLevelPropagator {
    @Shadow
    @Final
    private Long2ByteFunction idToLevel;

    @Shadow
    protected abstract int getLevel(long var1);

    @Shadow
    protected abstract int min(int int_1, int int_2);

    @Shadow
    private int minLevel;

    @Shadow
    @Final
    private int levelCount;

    @Shadow
    private volatile boolean hasUpdates;

    @Shadow
    @Final
    private LongLinkedOpenHashSet[] levelToIds;

    @Shadow
    protected abstract void updateMinLevel(int int_1);

    private Long2ObjectOpenHashMap<LongSet> idToLevelByChunk = new Long2ObjectOpenHashMap<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(int int_1, int int_2, int int_3, CallbackInfo ci) {
        this.idToLevelByChunk = new Long2ObjectOpenHashMap<>();
    }

    @Override
    public void bridge$removeOnUnload(long pos) {
        int level = this.idToLevel.get(pos) & 255;

        if (level != 255) {
            int existingLevel = this.getLevel(pos);
            int newLevel = this.min(existingLevel, level);

            this.removeFromLevelUnload(pos, newLevel, this.levelCount);

            this.hasUpdates = this.minLevel < this.levelCount;
        }
    }

    private void removeFromLevelUnload(long pos, int level, int min) {
        LongLinkedOpenHashSet set = this.levelToIds[level];
        set.remove(pos);

        this.idToLevel.remove(pos);

        if (set.isEmpty() && this.minLevel == level) {
            this.updateMinLevel(min);
        }
    }

    @Override
    public LongSet removeIdToLevelByChunk(long pos) {
        return this.idToLevelByChunk.remove(pos);
    }

    /**
     * Track additions to idToLevel and update the per-chunk cache.
     */
    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteFunction;put(JB)B"))
    private byte put(Long2ByteFunction long2ByteFunction, long key, byte value) {
        byte ret = long2ByteFunction.put(key, value);

        if (ret == (byte) -1) {
            LongSet set = this.idToLevelByChunk.computeIfAbsent(ChunkSectionPos.toChunkLong(key), unused -> new LongOpenHashSet());
            set.add(key);
        }

        return ret;
    }

    /**
     * Track removals from idToLevel and update the per-chunk cache.
     */
    @Redirect(method = {"removeFromLevel", "updateAllRecursively"}, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ByteFunction;remove(J)B"))
    private byte remove(Long2ByteFunction func, long key) {
        byte ret = func.remove(key);

        if (ret != (byte) -1) {
            LongSet set = this.removeIdToLevelByChunk(ChunkSectionPos.toChunkLong(key));

            if (set != null) {
                set.remove(key);
            }
        }

        return ret;
    }

}
