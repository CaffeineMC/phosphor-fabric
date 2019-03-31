package me.jellysquid.mods.phosphor.core;

import me.jellysquid.mods.phosphor.common.config.PhosphorConfig;
import me.jellysquid.mods.phosphor.common.util.ThreadUtil;
import net.minecraftforge.fml.relauncher.IFMLCallHook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

public class PhosphorFMLSetupHook implements IFMLCallHook {
    private static final Logger logger = LogManager.getLogger("Phosphor");

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public Void call() {
        logger.info("Phosphor has been hooked, starting initialization");

        logger.info("Loading configuration");

        PhosphorConfig config = PhosphorConfig.loadConfig();

        if (!config.enablePhosphor) {
            logger.info("Phosphor has been disabled through configuration, terminating setup hook");

            return null;
        }

        ThreadUtil.VALIDATE_THREAD_ACCESS = config.enableIllegalThreadAccessWarnings;

        logger.info("Loading Mixin framework");

        MixinBootstrap.init();

        Mixins.addConfiguration("mixins.phosphor.json");

        logger.info("Phosphor has finished initializing");

        return null;
    }
}
