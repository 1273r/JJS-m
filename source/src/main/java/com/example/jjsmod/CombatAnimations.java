package com.example.jjsmod;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Lightweight JJS-style player posing layered over vanilla player rendering.
 *
 * The server still owns hit detection and swing replication. This class only
 * changes the rendered pose, so it cannot create client/server combat desync.
 */
@EventBusSubscriber(modid = JJSMod.MODID, value = Dist.CLIENT)
public final class CombatAnimations {
    private CombatAnimations() {}

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        PlayerModel<?> model = event.getRenderer().getModel();

        // A tighter, more forward-driving punch arc than the default Minecraft swing.
        float swing = player.getAttackAnim(event.getPartialTick());
        if (swing > 0.001f) {
            float arc = (float) Math.sin(Math.sqrt(swing) * Math.PI);
            float recoil = (float) Math.sin(swing * Math.PI);
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            float side = right ? 1.0f : -1.0f;
            ModelPart arm = right ? model.rightArm : model.leftArm;
            ModelPart offArm = right ? model.leftArm : model.rightArm;

            arm.xRot = -1.72f + arc * 1.05f;
            arm.yRot = side * (-0.16f + recoil * 0.28f);
            arm.zRot = side * (-0.10f + recoil * 0.16f);

            // Counter-rotation gives the strike some weight instead of a detached arm flap.
            model.body.yRot += -side * recoil * 0.12f;
            offArm.xRot -= arc * 0.12f;
        }

        // The local player's guard pose is driven by the authoritative HUD state.
        if (player == net.minecraft.client.Minecraft.getInstance().player && ClientState.active && ClientState.blocking) {
            model.rightArm.xRot = -1.42f;
            model.leftArm.xRot = -1.42f;
            model.rightArm.yRot = -0.32f;
            model.leftArm.yRot = 0.32f;
            model.rightArm.zRot = -0.08f;
            model.leftArm.zRot = 0.08f;
        }
    }
}
