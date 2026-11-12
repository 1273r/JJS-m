package com.example.jjsmod;

public class ClientState {
    public static boolean active;
    public static String cls = "";
    public static boolean awakened, blocking, guardBroken, bfCue;
    public static float hp, maxHp, guard, awake;
    public static int[] left = new int[6];
    public static int[] total = new int[6];

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
    }
}
