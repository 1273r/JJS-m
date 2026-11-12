package com.example.jjsmod;

public class ClientState {
    public static boolean active;
    public static String cls = "";
    public static boolean awakened, blocking, guardBroken, bfCue;
    public static float hp, maxHp, guard, awake;
    public static int[] left = new int[6];
    public static int[] total = new int[6];

    // Immediate local animation state: inputs animate before the server HUD packet returns.
    public static int attackTicks, attackStage, dashTicks;
    public static float blockBlend;

    public static void startAttack() {
        attackStage = attackStage % 4 + 1;
        attackTicks = 9;
    }

    public static void startDash() {
        dashTicks = 7;
    }

    public static void tickAnimations() {
        if (attackTicks > 0) attackTicks--;
        if (dashTicks > 0) dashTicks--;
        float target = blocking ? 1f : 0f;
        blockBlend += (target - blockBlend) * 0.42f;
    }

    public static void apply(HudPayload p) {
        active = !p.cls().isEmpty();
        cls = p.cls();
        awakened = p.awakened();
        hp = p.hp();
        maxHp = p.maxHp();
        guard = p.guard();
        awake = p.awake();
        blocking = p.blocking();
        guardBroken = p.guardBroken();
        bfCue = p.bfCue();
        left = p.left();
        total = p.total();
    }

    public static void reset() {
        apply(HudPayload.empty());
        attackTicks = 0;
        attackStage = 0;
        dashTicks = 0;
        blockBlend = 0f;
    }
}
