package me.jellysquid.mods.phosphor.mod;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        name = PhosphorConstants.MOD_NAME,
        modid = PhosphorConstants.MOD_ID,
        version = PhosphorConstants.MOD_VERSION,
        certificateFingerprint = PhosphorConstants.MOD_FINGERPRINT,
        acceptedMinecraftVersions = "1.12.2",
        acceptableRemoteVersions = "*",
        dependencies = PhosphorConstants.MOD_DEPENDENCIES
)
public class PhosphorMod {
    public static final Logger LOGGER = LogManager.getLogger("Phosphor");
}
