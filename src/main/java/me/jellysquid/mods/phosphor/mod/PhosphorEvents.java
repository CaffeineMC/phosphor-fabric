package me.jellysquid.mods.phosphor.mod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(Side.CLIENT)
public class PhosphorEvents {
    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinWorldEvent event) {
        if (!PhosphorFlags.DISPLAY_PATREON || !event.getWorld().isRemote) {
            return;
        }

        if (!(event.getEntity() instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.getEntity();

        PhosphorConfig config = PhosphorConfig.instance();

        if (!config.enablePhosphor || !config.showPatreonMessage) {
            return;
        }

        player.sendMessage(new TextComponentString(TextFormatting.GOLD.toString() +
                "Thanks for installing Phosphor! ❤ You can pledge a cup of coffee to me every month " +
                "so you can look forward to future mods like this one. " +
                TextFormatting.YELLOW.toString() + TextFormatting.UNDERLINE.toString() + "https://patreon.com/jellysquid"));

        config.showPatreonMessage = false;
        config.saveConfig();
    }
}
