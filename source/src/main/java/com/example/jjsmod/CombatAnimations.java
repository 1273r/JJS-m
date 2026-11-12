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
        boolean local = player == net.minecraft.client.Minecraft.getInstance().player;

        // Other players retain their replicated vanilla swing. The local player gets
        // an immediate custom pose so input does not wait for a server round trip.
        if (local && ClientState.active) {
            float block = ClientState.blockBlend;
            if (ClientState.attackTicks > 0 && block < 0.15f) {
                float t = 1f - ClientState.attackTicks / 9f;
                float wind = smooth(Math.min(1f, t / 0.30f));
                float strike = smooth(Math.max(0f, Math.min(1f, (t - 0.30f) / 0.42f)));
                float recover = 1f - smooth(Math.max(0f, (t - 0.72f) / 0.28f));
                boolean right = (ClientState.attackStage & 1) == 1;
                ModelPart arm = right ? model.rightArm : model.leftArm;
                ModelPart support = right ? model.leftArm : model.rightArm;
                float side = right ? 1f : -1f;
                boolean finisher = ClientState.attackStage == 4;

                // Load the shoulder back, drive the fist through the target, then recoil.
                arm.xRot = -0.28f - wind * 0.78f - strike * (finisher ? 1.78f : 1.30f)
                        + recover * 0.38f;
                arm.yRot = side * (-0.10f + wind * 0.22f - strike * 0.12f);
                arm.zRot = side * (0.10f + wind * 0.20f - strike * 0.08f);
                support.xRot = -0.12f - strike * 0.22f;
                model.body.yRot += -side * strike * (finisher ? 0.30f : 0.20f);
                model.body.xRot -= strike * (finisher ? 0.10f : 0.045f);
                model.head.yRot += side * strike * 0.035f;
            }

            if (ClientState.dashTicks > 0 && block < 0.15f) {
                float t = ClientState.dashTicks / 7f;
                model.body.xRot = -0.20f - (1f - t) * 0.22f;
                model.body.yRot += (float) Math.sin((1f - t) * Math.PI) * 0.10f;
                model.rightArm.xRot = -0.95f;
                model.leftArm.xRot = -0.95f;
                model.rightArm.zRot = -0.42f;
                model.leftArm.zRot = 0.42f;
                model.rightLeg.xRot = 0.55f;
                model.leftLeg.xRot = -0.35f;
            }

            if (block > 0.01f) {
                float b = Math.min(1f, block);
                model.rightArm.xRot = lerp(model.rightArm.xRot, -1.48f, b);
                model.leftArm.xRot = lerp(model.leftArm.xRot, -1.48f, b);
                model.rightArm.yRot = lerp(model.rightArm.yRot, -0.38f, b);
                model.leftArm.yRot = lerp(model.leftArm.yRot, 0.38f, b);
                model.rightArm.zRot = lerp(model.rightArm.zRot, -0.08f, b);
                model.leftArm.zRot = lerp(model.leftArm.zRot, 0.08f, b);
                model.body.xRot -= 0.06f * b;
            }
        } else {
            // Preserve a more compact punch pose for non-local players' normal swing.
            float swing = player.getAttackAnim(event.getPartialTick());
            if (swing > 0.001f) {
                float arc = (float) Math.sin(Math.sqrt(swing) * Math.PI);
                boolean right = player.getMainArm() == HumanoidArm.RIGHT;
                float side = right ? 1.0f : -1.0f;
                ModelPart arm = right ? model.rightArm : model.leftArm;
                arm.xRot = -1.35f + arc * 0.82f;
                arm.yRot = side * -0.12f;
                arm.zRot = side * -0.08f;
                model.body.yRot += -side * arc * 0.10f;
            }
        }
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
