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
                boolean finisher = ClientState.cls.equals(Kits.TOJI) && ClientState.tojiWeapon != 2
                        ? ClientState.attackStage == 3 : ClientState.attackStage == 4;

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

            if (ClientState.skillTicks > 0 && block < 0.15f) {
                applySkillPose(model, ClientState.skillSlot, ClientState.skillTicks);
            }

            if (ClientState.hitReactTicks > 0 && block < 0.15f) {
                float react = ClientState.hitReactTicks / 7f;
                model.body.xRot += 0.22f * react;
                model.head.xRot -= 0.10f * react;
                model.rightArm.xRot = -0.55f * react;
                model.leftArm.xRot = -0.55f * react;
                model.rightArm.zRot = -0.28f * react;
                model.leftArm.zRot = 0.28f * react;
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

    /** Slot-specific windup/impact/recovery silhouettes. The phase is intentionally
     * exaggerated so a move reads clearly even with Minecraft's compact player rig. */
    private static void applySkillPose(PlayerModel<?> m, int slot, int ticks) {
        if (ClientState.cls.equals(Kits.TOJI)) {
            float p = 1f - ticks / (slot == 4 ? 22f : 26f);
            float snap = smooth(Math.max(0f, Math.min(1f, (p - 0.28f) / 0.30f)));
            if (slot == 1 || slot == 2) {
                int weapon = ClientState.tojiWeapon;
                m.body.xRot -= 0.08f + snap * 0.12f;
                m.rightArm.xRot = weapon == 0 ? -0.45f - snap * 1.5f : weapon == 1 ? -0.7f - snap * 1.25f : -0.25f - snap * 1.1f;
                m.leftArm.xRot = -0.35f - snap * 0.55f;
                m.rightArm.yRot = -0.25f + (float)Math.sin(p * Math.PI * 5) * 0.18f;
                m.rightArm.zRot = -0.18f;
                if (weapon == 2) { m.leftArm.zRot = 0.28f; m.body.yRot += (float)Math.sin(p * Math.PI * 6) * 0.12f; }
            } else if (slot == 4) {
                m.body.yRot += (float)Math.sin(p * Math.PI * 2) * 0.12f;
                m.rightArm.xRot = -0.9f; m.leftArm.xRot = -0.9f;
            }
            return;
        }
        float progress = 1f - ticks / (slot == 5 ? 34f : slot == 4 ? 22f : 26f);
        float wind = smooth(Math.min(1f, progress / 0.42f));
        float hit = smooth(Math.max(0f, Math.min(1f, (progress - 0.42f) / 0.22f)));
        float recoil = smooth(Math.max(0f, (progress - 0.68f) / 0.32f));
        float pulse = (float) Math.sin(progress * Math.PI * 9.0);
        switch (slot) {
            case 0 -> { // Blue: gather inward, then thrust both hands to pull
                m.body.xRot -= 0.10f + hit * 0.12f;
                m.rightArm.xRot = -0.65f - wind * 0.65f - hit * 0.55f + recoil * 0.3f;
                m.leftArm.xRot = -0.65f - wind * 0.65f - hit * 0.55f + recoil * 0.3f;
                m.rightArm.yRot = -0.22f + wind * 0.28f;
                m.leftArm.yRot = 0.22f - wind * 0.28f;
                m.rightArm.zRot = -0.18f;
                m.leftArm.zRot = 0.18f;
            }
            case 1 -> { // Red: one-arm charge and explosive straight release
                m.body.yRot += -0.18f * wind + 0.22f * hit;
                m.body.xRot -= 0.08f * wind + 0.12f * hit;
                m.rightArm.xRot = -0.35f - wind * 1.25f + hit * 1.65f + recoil * 0.25f;
                m.rightArm.yRot = -0.35f + wind * 0.45f;
                m.rightArm.zRot = -0.28f - wind * 0.22f;
                m.leftArm.xRot = -0.35f - hit * 0.3f;
            }
            case 2 -> { // Rapid Punches: alternating chest-level barrage
                m.body.yRot += pulse * 0.12f;
                m.body.xRot -= 0.10f;
                m.rightArm.xRot = -1.05f - Math.max(0f, pulse) * 0.95f;
                m.leftArm.xRot = -1.05f - Math.max(0f, -pulse) * 0.95f;
                m.rightArm.zRot = -0.12f;
                m.leftArm.zRot = 0.12f;
            }
            case 3 -> { // Twofold Kick: chamber, high kick, then recoil
                m.body.xRot += 0.08f + hit * 0.18f;
                m.body.yRot += 0.08f * wind;
                m.rightLeg.xRot = -0.25f - wind * 1.45f + hit * 0.65f + recoil * 0.25f;
                m.rightLeg.yRot = -0.08f;
                m.leftLeg.xRot = 0.18f + hit * 0.12f;
                m.rightArm.xRot = -0.8f;
                m.leftArm.xRot = -0.65f;
            }
            case 4 -> { // Limitless: short spatial fold / palm-forward materialization
                m.body.xRot -= 0.12f * wind;
                m.body.yRot += (float) Math.sin(progress * Math.PI) * 0.32f;
                m.rightArm.xRot = -0.45f - wind * 1.0f - hit * 0.45f;
                m.leftArm.xRot = -0.45f - wind * 0.65f;
                m.rightArm.yRot = -0.25f;
                m.leftArm.yRot = 0.25f;
            }
            case 5 -> { // Domain hand sign: both forearms rise and close inward
                float sign = smooth(Math.min(1f, progress / 0.5f));
                m.body.xRot -= 0.12f * sign;
                m.rightArm.xRot = -1.25f * sign;
                m.leftArm.xRot = -1.25f * sign;
                m.rightArm.yRot = -0.42f * sign;
                m.leftArm.yRot = 0.42f * sign;
                m.rightArm.zRot = -0.20f * sign;
                m.leftArm.zRot = 0.20f * sign;
                m.head.xRot -= 0.06f * sign;
            }
            default -> { }
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
