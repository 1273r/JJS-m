package com.example.jjsmod;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Controls: LMB = M1, Q = dash (W/A/S/D picks the direction), hold F = block,
 * 1-4 = moves, R = special, X = domain, G = awaken.
 */
@EventBusSubscriber(modid = JJSMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static boolean blockHeld;

    private static void send(int action, float a, float b) {
        PacketDistributor.sendToServer(new ActionPayload(action, a, b));
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.active || mc.player == null) return;
        int key = e.getKey();
        int action = e.getAction();

        if (key == GLFW.GLFW_KEY_F) {
            if (action == GLFW.GLFW_PRESS && mc.screen == null && !blockHeld) {
                blockHeld = true;
                ClientState.blocking = true;
                send(Net.BLOCK_ON, 0, 0);
            } else if (action == GLFW.GLFW_RELEASE && blockHeld) {
                blockHeld = false;
                ClientState.blocking = false;
                send(Net.BLOCK_OFF, 0, 0);
            }
            return;
        }
        if (action != GLFW.GLFW_PRESS || mc.screen != null) return;

        if (key == GLFW.GLFW_KEY_Q) {
            Input in = mc.player.input;
            ClientState.startDash();
            send(Net.DASH, in.forwardImpulse, in.leftImpulse);
        } else if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
            send(Net.SKILL, key - GLFW.GLFW_KEY_1, 0);
        } else if (key == GLFW.GLFW_KEY_R) {
            send(Net.SKILL, 4, 0);
        } else if (key == GLFW.GLFW_KEY_X) {
            send(Net.SKILL, 5, 0);
        } else if (key == GLFW.GLFW_KEY_G) {
            send(Net.AWAKEN, 0, 0);
        }
    }

    /** Left click = M1. Cancels the vanilla attack. */
    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered e) {
        if (!ClientState.active || !e.isAttack()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        ClientState.startAttack();
        send(Net.M1, 0, 0);
    }

    /** Swallow vanilla uses of our keys (drop item, swap hands, hotbar slots). */
    @SubscribeEvent
    public static void onTickPre(ClientTickEvent.Pre e) {
        if (!ClientState.active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        while (mc.options.keyDrop.consumeClick()) { }
        while (mc.options.keySwapOffhand.consumeClick()) { }
        for (KeyMapping k : mc.options.keyHotbarSlots) {
            while (k.consumeClick()) { }
        }
    }

    /** Safety: release block if F is no longer held (e.g. a screen opened). */
    @SubscribeEvent
    public static void onTickPost(ClientTickEvent.Post e) {
        ClientState.tickAnimations();
        Minecraft mc = Minecraft.getInstance();
        if (blockHeld && (mc.screen != null || !InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F))) {
            blockHeld = false;
            ClientState.blocking = false;
            send(Net.BLOCK_OFF, 0, 0);
        }
    }

    /** Remove the health, hunger, armor, XP and hotbar - the JJS HUD replaces them. */
    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre e) {
        if (!ClientState.active) return;
        if (e.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)
                || e.getName().equals(VanillaGuiLayers.FOOD_LEVEL)
                || e.getName().equals(VanillaGuiLayers.ARMOR_LEVEL)
                || e.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || e.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
                || e.getName().equals(VanillaGuiLayers.HOTBAR)
                || e.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientState.reset();
        blockHeld = false;
    }
}
