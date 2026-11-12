package com.example.jjsmod;

public class ClientState {
    public static boolean active;
    public static String cls = "";
    public static boolean awakened, blocking, guardBroken, bfCue;
    public static float hp, maxHp, guard, awake;
    public static int[] left = new int[6];
    public static int[] total = new int[6];

    // Immediate local animation state: inputs animate before the server HUD packet returns.
    public static int attackTicks, attackStage, dashTicks, hitReactTicks;
    public static int skillTicks, skillSlot = -1;
    public static float blockBlend;

    public static void startAttack() {
        attackStage = attackStage % 4 + 1;
        attackTicks = 9;
    }

    public static void startDash() {
        dashTicks = 9;
        skillTicks = 0;
        skillSlot = -1;
    }

    /** Predict a distinct move pose locally; server remains authoritative for the move. */
    public static void startSkill(int slot) {
        skillSlot = slot;
        skillTicks = slot == 5 ? 34 : slot == 4 ? 22 : 26;
        attackTicks = 0;
        dashTicks = 0;
    }

    public static void tickAnimations() {
        if (attackTicks > 0) attackTicks--;
        if (dashTicks > 0) dashTicks--;
        if (hitReactTicks > 0) hitReactTicks--;
        if (skillTicks > 0) skillTicks--;
        else skillSlot = -1;
        float target = blocking ? 1f : 0f;
        blockBlend += (target - blockBlend) * 0.42f;
    }

    public static void apply(HudPayload p) {
        if (active && p.hp() < hp - 0.01f) hitReactTicks = 7;
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
        hitReactTicks = 0;
        skillTicks = 0;
        skillSlot = -1;
        blockBlend = 0f;
    }
}
