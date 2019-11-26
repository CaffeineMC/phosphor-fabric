package me.jellysquid.mods.phosphor.common;

import net.fabricmc.api.ModInitializer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@SuppressWarnings("unused")
public class PhosphorMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Log log = LogFactory.getLog("Phosphor");
        log.warn("This is an early-access version of Phosphor which could cause issues in your game. Please be mindful.");
    }
}
