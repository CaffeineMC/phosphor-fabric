package me.jellysquid.mods.phosphor.common;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("unused")
public class PhosphorMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Logger log = LogManager.getLogger("Phosphor");
        log.warn("Phosphor has been initialized. Please note that you are using an early release of Phosphor which " +
                "*could* result in unexpected issues or crashes. You can join the Discord server to get support here: https://discord.gg/X2TuKe");
    }
}
