package com.example.jjsmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-player runtime combat state (server side). */
public class JState {
    public static final float MAX_GUARD = 100f;
    public static final int AWAKEN_TICKS = 1200; // 60 s

    private static final Map<UUID, JState> MAP = new HashMap<>();

    public final String cls;
    public final Map<String, Long> cdEnd = new HashMap<>();
    public Cast cast;
    public long lockUntil, stunUntil, m1Ready, lastM1, dashReady, counterUntil, guardBrokenUntil, bulletUntil, awakeEnd;
    public int m1;
    /** One queued M1 input, accepted shortly before the current recovery ends. */
    public boolean m1Buffered;
    public long m1BufferUntil;
    public boolean blocking, awakened;
    public float guard = MAX_GUARD;
    public float awake;
    /** Target captured by Rapid Punches for the follow-up Limitless / Face Grater variant. */
    public UUID lastRapidTarget;
    public long lastRapidAt;
    public int tojiWeapon;
    public long tojiVariantUntil;
    public UUID tojiTarget;

    private JState(String cls) {
        this.cls = cls;
    }

    public static JState peek(ServerPlayer p) {
        String cls = p.getPersistentData().getString("jjs_class");
        if (cls.isEmpty()) {
            MAP.remove(p.getUUID());
            return null;
        }
        JState s = MAP.get(p.getUUID());
        if (s == null || !s.cls.equals(cls)) {
            s = new JState(cls);
            MAP.put(p.getUUID(), s);
        }
        return s;
    }

    public static void drop(ServerPlayer p) {
        MAP.remove(p.getUUID());
    }

    public static void clearAll() {
        MAP.clear();
    }

    public boolean free(long now) {
        return now >= stunUntil && now >= lockUntil;
    }

    public int cdLeft(String id, long now) {
        Long e = cdEnd.get(id);
        return e == null ? 0 : (int) Math.max(0, e - now);
    }

    public void setCd(String id, int ticks, long now) {
        cdEnd.put(id, now + ticks);
    }

    public void addAwake(float v) {
        if (!awakened) awake = Math.min(100f, awake + v);
    }

    public void sync(ServerPlayer p) {
        long now = p.level().getGameTime();
        String[] slots = cls.equals(Kits.TOJI) ? Kits.tojiSlots(tojiWeapon) : Kits.slots(cls, awakened);
        int[] left = new int[6];
        int[] total = new int[6];
        for (int i = 0; i < 6; i++) {
            if (!slots[i].isEmpty()) {
                left[i] = cdLeft(slots[i], now);
                total[i] = Kits.cd(slots[i]);
            }
        }
        boolean cue = false;
        if (cast != null && cast.id.equals("divergent_fist") && cast.live(p) && !cast.bf && !cast.bfLocked) {
            long dt = now - cast.start;
            cue = dt >= Moves.BF_START && dt <= Moves.BF_END;
        }
        float meter = awakened ? 100f * Math.max(0, awakeEnd - now) / AWAKEN_TICKS : awake;
        PacketDistributor.sendToPlayer(p, new HudPayload(cls, awakened, p.getHealth(), p.getMaxHealth(),
                guard, meter, blocking, now < guardBrokenUntil, cue, tojiWeapon, left, total));
    }
}
