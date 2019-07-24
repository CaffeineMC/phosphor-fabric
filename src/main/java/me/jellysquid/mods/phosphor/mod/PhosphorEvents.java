package me.jellysquid.mods.phosphor.mod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.CertificateHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(Side.CLIENT)
public class PhosphorEvents {
    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote) {
            return;
        }

        PhosphorConfig config = PhosphorConfig.instance();

        if (!config.enablePhosphor || !config.showPatreonMessage) {
            return;
        }

        if (!(event.getEntity() instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.getEntity();

        if (PhosphorEvents.isAetherInstalled()) {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "The Aether II includes the Phosphor mod! ❤ " + TextFormatting.GOLD +
                    "You can help fund the development of Phosphor with a pledge to the author's Patreon. " + TextFormatting.YELLOW + TextFormatting.UNDERLINE + "Click this link to make a pledge!")
                    .setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.patreon.com/jellysquid"))));
        } else {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Thanks for installing Phosphor! ❤ " + TextFormatting.GOLD + "You can help fund the development of " +
                    "future optimization mods like this one through pledging to my Patreon. " + TextFormatting.YELLOW + TextFormatting.UNDERLINE + "Click this link to make a pledge!")
                    .setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.patreon.com/jellysquid"))));
        }

        config.showPatreonMessage = false;
        config.saveConfig();
    }

    private static boolean isAetherInstalled() {
        if (!Loader.isModLoaded("aether")) {
            return false;
        }

        ModContainer mod = Loader.instance().getIndexedModList().get("aether");

        if (mod == null) {
            return false;
        }

        String fingerprint = CertificateHelper.getFingerprint(mod.getSigningCertificate());

        return fingerprint.equals("db341c083b1b8ce9160a769b569ef6737b3f4cdf");
    }
}
