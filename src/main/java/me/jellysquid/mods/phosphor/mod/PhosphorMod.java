package me.jellysquid.mods.phosphor.mod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(name = PhosphorConstants.MOD_NAME,
        modid = PhosphorConstants.MOD_ID,
        version = PhosphorConstants.MOD_VERSION,
        certificateFingerprint = PhosphorConstants.MOD_FINGERPRINT,
        acceptedMinecraftVersions = "1.12.2",
        acceptableRemoteVersions = "*")
public class PhosphorMod {
    public static final Logger LOGGER = LogManager.getLogger("Phosphor");

    @SidedProxy(clientSide = "me.jellysquid.mods.phosphor.mod.client.PhosphorProxyClient",
            serverSide = "me.jellysquid.mods.phosphor.mod.server.PhosphorProxyServer")
    public static PhosphorProxy PROXY;
}
