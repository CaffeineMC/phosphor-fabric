package me.jellysquid.mods.phosphor.common;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(name = PhosphorMod.MOD_NAME,
        modid = PhosphorMod.MOD_ID,
        version = PhosphorMod.MOD_VERSION,
        certificateFingerprint = PhosphorMod.MOD_FINGERPRINT)
public class PhosphorMod {

    public static final String MOD_ID = "phosphor-lighting";

    public static final String MOD_NAME = "Phosphor";

    public static final String MOD_VERSION = "1.12.2-0.1.0";

    public static final String MOD_FINGERPRINT = "db341c083b1b8ce9160a769b569ef6737b3f4cdf";

    public static final Logger LOGGER = LogManager.getLogger("AetherII");

    @Mod.Instance(PhosphorMod.MOD_ID)
    public static PhosphorMod INSTANCE;

    @SidedProxy(clientSide = "me.jellysquid.mods.phosphor.client.PhosphorProxyClient",
            serverSide = "me.jellysquid.mods.phosphor.server.PhosphorProxyServer")
    public static PhosphorProxy PROXY;

}
