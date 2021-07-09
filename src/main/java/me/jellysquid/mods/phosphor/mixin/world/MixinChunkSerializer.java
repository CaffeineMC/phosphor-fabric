package me.jellysquid.mods.phosphor.mixin.world;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.poi.PointOfInterestStorage;

@Mixin(ChunkSerializer.class)
public abstract class MixinChunkSerializer {
    @Inject(
        method = "deserialize(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/structure/StructureManager;Lnet/minecraft/world/poi/PointOfInterestStorage;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/nbt/NbtCompound;)Lnet/minecraft/world/chunk/ProtoChunk;",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/chunk/ChunkManager;getLightingProvider()Lnet/minecraft/world/chunk/light/LightingProvider;",
            ordinal = 0
        )
    )
    private static void loadLightmaps(final ServerWorld world, final StructureManager structureManager, final PointOfInterestStorage poiStorage, final ChunkPos pos, final NbtCompound tag, final CallbackInfoReturnable<ProtoChunk> ci) {
        final NbtCompound levelTag = tag.getCompound("Level");

        // Load lightmaps of pre_light chunks unless erasing cached data
        if (levelTag.getBoolean("isLightOn") || !levelTag.contains("Heightmaps", 10)) {
            return;
        }

        final NbtList sections = levelTag.getList("Sections", 10);
        final LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
        final boolean hasSkyLight = world.getDimension().hasSkyLight();

        lightingProvider.setRetainData(pos, true);

        for(int i = 0; i < sections.size(); ++i) {
            final NbtCompound section = sections.getCompound(i);
            final int y = section.getByte("Y");

            if (section.contains("BlockLight", 7)) {
                lightingProvider.enqueueSectionData(LightType.BLOCK, ChunkSectionPos.from(pos, y), new ChunkNibbleArray(section.getByteArray("BlockLight")), true);
            }

            if (hasSkyLight && section.contains("SkyLight", 7)) {
                lightingProvider.enqueueSectionData(LightType.SKY, ChunkSectionPos.from(pos, y), new ChunkNibbleArray(section.getByteArray("SkyLight")), true);
            }
        }
    }
}
