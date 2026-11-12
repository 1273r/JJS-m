package com.example.jjsmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Installs the JJS pause menu only while the JJS moveset is enabled. */
@EventBusSubscriber(modid = JJSMod.MODID, value = Dist.CLIENT)
public final class JJSMenuEvents {
    private JJSMenuEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientState.active
                && minecraft.player != null
                && event.getScreen() instanceof PauseScreen) {
            event.setNewScreen(new JJSMenuScreen());
        }
    }
}
