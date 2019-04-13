package me.jellysquid.mods.phosphor.mixins.lighting.common;

import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExtendedBlockStorage.class)
public class MixinExtendedBlockStorage {
    @Shadow
    private NibbleArray skyLight;

    @Shadow
    private int blockRefCount;

    @Shadow
    private NibbleArray blockLight;

    private int lightRefCount = -1;

    /**
     * @author Angeline
     * @author Reset lightRefCount on call
     */
    @Overwrite
    public void setSkyLight(int x, int y, int z, int value) {
        this.skyLight.set(x, y, z, value);
        this.lightRefCount = -1;
    }

    /**
     * @author Angeline
     * @author Reset lightRefCount on call
     */
    @Overwrite
    public void setBlockLight(int x, int y, int z, int value) {
        this.blockLight.set(x, y, z, value);
        this.lightRefCount = -1;
    }

    /**
     * @author Angeline
     * @author Reset lightRefCount on call
     */
    @Overwrite
    public void setBlockLight(NibbleArray array) {
        this.blockLight = array;
        this.lightRefCount = -1;
    }

    /**
     * @author Angeline
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setSkyLight(NibbleArray array) {
        this.skyLight = array;
        this.lightRefCount = -1;
    }


    /**
     * @author Angeline
     * @reason Send light data to clients when lighting is non-trivial
     */
    @Overwrite
    public boolean isEmpty() {
        if (this.blockRefCount != 0) {
            return false;
        }

        // -1 indicates the lightRefCount needs to be re-calculated
        if (this.lightRefCount == -1) {
            if (this.checkLightArrayEqual(this.skyLight, (byte) 0xFF)
                    && this.checkLightArrayEqual(this.blockLight, (byte) 0x00)) {
                this.lightRefCount = 0; // Lighting is trivial, don't send to clients
            } else {
                this.lightRefCount = 1; // Lighting is not trivial, send to clients
            }
        }

        return this.lightRefCount == 0;
    }

    private boolean checkLightArrayEqual(NibbleArray storage, byte val) {
        if (storage == null) {
            return true;
        }

        byte[] arr = storage.getData();

        for (byte b : arr) {
            if (b != val) {
                return false;
            }
        }

        return true;
    }
}
