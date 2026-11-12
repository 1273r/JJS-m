package com.example.jjsmod;

import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Basic combat (M1 / dash / block) and every move.
 * Numbers marked [wiki] come from the JJS fandom wiki figures (100 HP scale, which is why
 * players get 100 max health). Numbers marked [est] are my estimates - the wikis don't publish them.
 */
public class Moves {
    /** Black Flash window, in ticks after Divergent Fist starts (20 ticks = 1 s). Press 3 again inside it. */
    public static final int BF_START = 6;
    public static final int BF_END = 11;

    // =====================================================================
    //  helpers
    // =====================================================================

    private static ServerLevel lv(ServerPlayer p) {
        return Combat.lv(p);
    }

    private static long now(ServerPlayer p) {
        return Combat.now(p);
    }

    private static Cast begin(ServerPlayer p, JState st, String id, boolean interruptible, int windup, int lock) {
        long n = now(p);
        Cast c = new Cast(id, interruptible, n, n + windup);
        st.m1Buffered = false;
        st.cast = c;
        st.lockUntil = n + lock;
        st.setCd(id, Kits.cd(id), n);
        st.m1 = 0;
        return c;
    }

    /** Runs body(t) once per tick (t = 1..duration) while the cast is alive. */
    private static void run(ServerPlayer p, JState st, Cast c, int duration, IntConsumer body) {
        int[] t = {0};
        Tasks.add(() -> {
            if (!c.live(p)) {
                if (st.cast == c) st.cast = null;
                return true;
            }
            t[0]++;
            body.accept(t[0]);
            if (t[0] >= duration) {
                if (st.cast == c) st.cast = null;
                return true;
            }
            return false;
        });
    }

    public static void cancelCast(JState st) {
        if (st.cast != null) {
            st.cast.cancelled = true;
            st.cast = null;
        }
        st.lockUntil = 0;
    }

    public static void stun(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
        if (t instanceof ServerPlayer sp) {
            JState s = JState.peek(sp);
            if (s != null) {
                s.stunUntil = Math.max(s.stunUntil, now(sp) + ticks);
                s.blocking = false;
                cancelCast(s);
            }
        }
    }

    private static boolean isBusy(LivingEntity t) {
        if (t instanceof ServerPlayer sp) {
            JState s = JState.peek(sp);
            return s != null && !s.blocking && s.cast != null && s.cast.live(sp) && s.cast.interruptible;
        }
        return false;
    }

    private static void impactFx(ServerPlayer p, LivingEntity t, float r, float g, float b) {
        Vec3 c = Combat.mid(t);
        Fx.dust(lv(p), c, r, g, b, 2.0f, 18, 0.4);
        Fx.sfx(lv(p), c, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
    }

    private static void sweepFx(ServerPlayer p, Vec3 c) {
        lv(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
    }

    // =====================================================================
    //  basic combat
    // =====================================================================

    /**
     * Four-stage JJS-style M1 chain. The server owns timing and hit registration;
     * the forgiving melee volume, small forward carry, hitstun and distinct finisher
     * recovery make the chain feel less like four isolated vanilla clicks.
     */
    public static void m1(ServerPlayer p, JState st) {
        long n = now(p);
        if (st.blocking || !st.free(n)) return;

        if (n < st.m1Ready) {
            if (st.m1Ready - n <= 7) {
                st.m1Buffered = true;
                st.m1BufferUntil = n + 8;
            }
            return;
        }

        st.m1Buffered = false;
        if (n - st.lastM1 > 26) st.m1 = 0;
        int maxChain = Kits.TOJI.equals(st.cls) && st.tojiWeapon != 2 ? 3 : 4;
        st.m1++;
        if (st.m1 > maxChain) st.m1 = 1;

        final int chain = st.m1;
        final boolean finisher = chain == maxChain;
        st.lastM1 = n;
        st.m1Ready = n + (finisher ? 19 : 7);

        p.swing(InteractionHand.MAIN_HAND, true);
        Vec3 forward = Combat.flat(p);
        boolean shrine = st.awakened && Kits.VESSEL.equals(st.cls);
        double range = shrine ? 24 * Combat.STUD : Kits.TOJI.equals(st.cls) ? (st.tojiWeapon == 2 ? 9 : st.tojiWeapon == 1 ? 7 : 3.55) * Combat.STUD : 3.55;
        List<LivingEntity> targets = shrine
                ? Combat.line(p, range, 1.0)
                : Combat.melee(p, range, finisher ? 1.45 : 1.20);

        // Small, capped forward carry gives every punch momentum without teleporting.
        if (!shrine && p.onGround()) {
            Vec3 velocity = p.getDeltaMovement();
            p.setDeltaMovement(velocity.x * 0.72 + forward.x * (finisher ? 0.20 : 0.14),
                    velocity.y, velocity.z * 0.72 + forward.z * (finisher ? 0.20 : 0.14));
            p.hurtMarked = true;
        }

        if (shrine) {
            Vec3 from = p.getEyePosition();
            Combat.trail(from, from.add(p.getLookAngle().scale(range)), 0.9,
                    point -> sweepFx(p, point));
        }

        if (targets.isEmpty()) {
            // Whiffs have a little extra recovery and break the chain, preventing
            // empty-air spam from preserving a fully primed fourth hit.
            st.m1 = 0;
            st.m1Ready = n + 10;
            Fx.sfx(lv(p), p.position(), SoundEvents.PLAYER_ATTACK_WEAK, 0.65f, finisher ? 0.82f : 1.05f);
            return;
        }

        LivingEntity target = targets.get(0);
        float damage = Kits.TOJI.equals(st.cls) ? (finisher ? 5.0f : (chain == 1 ? 4.0f : 5.0f)) : (finisher ? 5.0f : 2.5f);
        boolean landed = Combat.hit(p, target, damage,
                shrine ? Combat.BLOCK_360 : Combat.BLOCKABLE, true);
        if (!landed) {
            st.m1 = 0;
            st.m1Ready = n + 9;
            Fx.sfx(lv(p), p.position(), SoundEvents.SHIELD_BLOCK, 0.7f, 1.2f);
            return;
        }

        // Brief hitstun prevents the victim from instantly trading out of the string.
        stun(target, finisher ? 14 : 9);
        Vec3 away = Combat.away(p, target);
        if (finisher) {
            Combat.knock(target, away.x * 1.15, 0.34, away.z * 1.15);
            Fx.sfx(lv(p), Combat.mid(target), SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.82f);
            Fx.dust(lv(p), Combat.mid(target), 1.0f, 0.82f, 0.62f, 1.9f, 14, 0.42);
        } else {
            Combat.knock(target, away.x * 0.20, 0.08, away.z * 0.20);
            Fx.sfx(lv(p), Combat.mid(target), SoundEvents.PLAYER_ATTACK_STRONG, 0.85f, 0.92f + chain * 0.08f);
            Fx.dust(lv(p), Combat.mid(target), 0.92f, 0.94f, 1.0f, 1.25f, 8, 0.28);
        }
    }

    /** Q dash. Direction from W/A/S/D at press time, no input = back dash. Cooldowns are [est]. */
    public static void dash(ServerPlayer p, JState st, float fwd, float left) {
        long n = now(p);
        if (n < st.stunUntil || n < st.lockUntil || n < st.dashReady) return;
        boolean front = fwd > 0.5f && Math.abs(left) < 0.5f;
        if (Math.abs(fwd) < 0.1f && Math.abs(left) < 0.1f) fwd = -1f;
        double yaw = Math.toRadians(p.getYRot());
        double dx = left * Math.cos(yaw) - fwd * Math.sin(yaw);
        double dz = fwd * Math.cos(yaw) + left * Math.sin(yaw);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) return;
        double speed = fwd < 0 && Math.abs(left) < 0.5f ? 1.7 : 2.0;
        p.setDeltaMovement(dx / len * speed, 0.12, dz / len * speed);
        p.hurtMarked = true;
        st.dashReady = n + (front ? 60 : 40);
        st.m1Ready = n; // dash cancels M1 endlag
        st.m1Buffered = false;
        st.m1 = 0;
        Fx.sfx(lv(p), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
        lv(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
    }

    public static void setBlock(ServerPlayer p, JState st, boolean on) {
        long n = now(p);
        if (on && Kits.TOJI.equals(st.cls) && st.cast != null && st.cast.id.equals("flourish") && n <= st.tojiVariantUntil) { tojiPride(p, st); return; }
        if (on) {
            if (!st.free(n) || n < st.guardBrokenUntil) return;
            st.blocking = true;
        } else {
            st.blocking = false;
        }
    }

    // =====================================================================
    //  awakening
    // =====================================================================

    public static void awaken(ServerPlayer p, JState st) {
        long n = now(p);
        if (st.awakened || st.awake < 100f || !st.free(n)) return;
        if (Kits.TOJI.equals(st.cls)) {
            st.awake = 0f; st.cdEnd.clear(); p.heal(50f);
            Fx.sfx(lv(p), p.position(), SoundEvents.BEACON_ACTIVATE, 1.4f, 0.9f);
            Fx.dust(lv(p), p.position().add(0,1,0), 0.8f,0.75f,0.55f,2.4f,48,1.0);
            p.displayClientMessage(Component.literal("PUPPET OF CARNAGE — COOLDOWNS RESET"), true);
            return;
        }
        st.awakened = true;
        st.awakeEnd = n + JState.AWAKEN_TICKS;
        if (Kits.VESSEL.equals(st.cls)) {
            p.heal(45f); // King of Curses heals 45 [wiki]
        } else if (Kits.TOJI.equals(st.cls)) {
            p.heal(50f);
            st.cdEnd.clear();
        }
        ServerLevel l = lv(p);
        Fx.sfx(l, p.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
        Fx.dust(l, p.position().add(0, 1, 0), 1f, 0.2f, 0.2f, 2.5f, 60, 1.2);
        p.displayClientMessage(Component.literal(Kits.VESSEL.equals(st.cls) ? "King of Curses" : "Six Eyes"), true);
    }

    public static void endAwaken(ServerPlayer p, JState st) {
        st.awakened = false;
        st.awake = 0f;
        Fx.sfx(lv(p), p.position(), SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.8f);
    }

    // =====================================================================
    //  skill dispatch
    // =====================================================================

    public static void skill(ServerPlayer p, JState st, int slot) {
        String[] sl = Kits.slots(st.cls, st.awakened);
        if (slot < 0 || slot >= sl.length) return;
        String id = sl[slot];
        if (id.isEmpty()) return;
        long n = now(p);

        // second press of Divergent Fist = Black Flash attempt
        if (id.equals("divergent_fist") && st.cast != null && st.cast.id.equals("divergent_fist") && st.cast.live(p)) {
            blackFlashPress(p, st, n);
            return;
        }
        if (Kits.TOJI.equals(st.cls) && id.equals("hidden_inventory")) { tojiSwap(p, st, n); return; }
        if (Kits.TOJI.equals(st.cls) && tojiVariant(p, st, id, n)) return;
        if (id.equals("combat_instincts")) {
            combatInstincts(p, st, n);
            return;
        }
        // Special-input variants are checked before normal endlag gating, as in JJS.
        if (id.equals("limitless") && st.cast != null && st.cast.id.equals("reversal_red")
                && st.cast.live(p) && n - st.cast.start <= 12) {
            if (st.cdLeft("limitless", n) <= 0) reversalRedSpecial(p, st);
            return;
        }
        if (id.equals("limitless") && st.lastRapidTarget != null && n - st.lastRapidAt <= 50) {
            if (st.cdLeft("limitless", n) <= 0) faceGrater(p, st);
            return;
        }
        if (!st.free(n) || st.blocking || st.cdLeft(id, n) > 0) return;

        switch (id) {
            case "lapse_blue" -> lapseBlue(p, st);
            case "reversal_red" -> reversalRed(p, st);
            case "rapid_punches" -> rapidPunches(p, st);
            case "twofold_kick" -> twofoldKick(p, st);
            case "limitless" -> limitless(p, st);
            case "lapse_blue_max" -> lapseBlueMax(p, st);
            case "reversal_red_max" -> reversalRedMax(p, st);
            case "hollow_purple" -> hollowPurple(p, st);
            case "infinite_void" -> infiniteVoid(p, st);
            case "cursed_strikes" -> cursedStrikes(p, st);
            case "crushing_blow" -> crushingBlow(p, st);
            case "divergent_fist" -> divergentFist(p, st);
            case "manji_kick" -> manjiKick(p, st);
            case "cleave" -> cleave(p, st);
            case "dismantle" -> dismantle(p, st);
            case "open" -> open(p, st);
            case "rush" -> rush(p, st);
            case "malevolent_shrine" -> malevolentShrine(p, st);
            case "flourish" -> tojiFlourish(p, st);
            case "debilitate" -> tojiDebilitate(p, st);
            case "transcend" -> tojiTranscend(p, st);
            case "rupture" -> tojiRupture(p, st);
            case "lasso" -> tojiLasso(p, st);
            case "whiplash" -> tojiWhiplash(p, st);
            default -> { }
        }
    }

    /** Vessel special: cancel an M1 or a move's windup with no endlag; the cancelled move goes off cooldown. */
    private static void combatInstincts(ServerPlayer p, JState st, long n) {
        if (st.cdLeft("combat_instincts", n) > 0) return;
        st.setCd("combat_instincts", Kits.cd("combat_instincts"), n);
        Cast c = st.cast;
        if (c != null && c.live(p) && n < c.windupEnd) {
            st.cdEnd.remove(c.id);
            cancelCast(st);
            Fx.dust(lv(p), p.position().add(0, 1, 0), 1f, 1f, 1f, 1.5f, 15, 0.5);
            Fx.sfx(lv(p), p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.8f);
        }
        st.m1Ready = n;
    }

    // =====================================================================
    //  HONORED ONE
    // =====================================================================

    /** Lapse: Blue - 12.5 (5 pull + 7.5 kick), 13 s, 360-blockable. [wiki] */
    private static void lapseBlue(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "lapse_blue", true, 10, 30);
        LivingEntity[] tg = {null};
        run(p, st, c, 30, t -> {
            Vec3 orb = p.getEyePosition().add(p.getLookAngle().scale(2.0));
            if (t <= 10) Fx.dust(l, orb, 0.15f, 0.4f, 1f, 1.5f + t * 0.1f, 8, 0.3);
            if (t == 10) {
                Fx.sfx(l, orb, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.5f);
                List<LivingEntity> ls = Combat.line(p, 16, 1.8);
                if (!ls.isEmpty() && Combat.hit(p, ls.get(0), 5f, Combat.BLOCK_360, true)) tg[0] = ls.get(0);
            }
            if (t > 10 && t <= 16 && tg[0] != null && tg[0].isAlive()) {
                Vec3 want = p.position().add(Combat.flat(p).scale(1.6));
                Combat.knock(tg[0], want.subtract(tg[0].position()).scale(0.45));
                Fx.dust(l, Combat.mid(tg[0]), 0.15f, 0.4f, 1f, 1.5f, 6, 0.4);
            }
            if (t == 19 && tg[0] != null && tg[0].isAlive() && tg[0].distanceTo(p) <= 4.5) {
                p.swing(InteractionHand.MAIN_HAND, true);
                if (Combat.hit(p, tg[0], 7.5f, Combat.BLOCKABLE, true)) {
                    Vec3 a = Combat.away(p, tg[0]);
                    Combat.knock(tg[0], a.x * 1.4, 0.45, a.z * 1.4);
                    impactFx(p, tg[0], 0.2f, 0.4f, 1f);
                }
            }
        });
    }

    /** Reversal: Red - 12.5 (6.25 if blocked), 20 s. [wiki] */
    private static void reversalRed(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "reversal_red", true, 12, 26);
        run(p, st, c, 26, t -> {
            if (t <= 12) Fx.dust(l, p.getEyePosition().add(p.getLookAngle().scale(1.8)), 1f, 0.1f, 0.1f, 1.6f, 6, 0.25);
            if (t == 12) {
                Vec3 dir = p.getLookAngle();
                Vec3[] pos = {p.getEyePosition().add(dir.scale(1.5))};
                int[] n = {0};
                Fx.sfx(l, pos[0], SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
                Tasks.add(() -> {
                    n[0]++;
                    pos[0] = pos[0].add(dir.scale(1.6));
                    Vec3 cp = pos[0];
                    Fx.dust(l, cp, 1f, 0.1f, 0.1f, 2.2f, 10, 0.25);
                    BlockPos bp = BlockPos.containing(cp);
                    boolean solid = !l.getBlockState(bp).getCollisionShape(l, bp).isEmpty();
                    List<LivingEntity> es = Combat.around(p, cp, 1.3);
                    if (solid || !es.isEmpty() || n[0] >= 16) {
                        redImpact(p, cp, 3.0, 12.5f, 2.0, false);
                        return true;
                    }
                    return false;
                });
            }
        });
    }

    private static void redImpact(ServerPlayer p, Vec3 c, double radius, float dmg, double force, boolean scale) {
        ServerLevel l = lv(p);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        Fx.dust(l, c, 1f, 0.1f, 0.1f, 3f, 80, radius * 0.5);
        Fx.sfx(l, c, SoundEvents.GENERIC_EXPLODE, 1.5f, 1.2f);
        for (LivingEntity e : Combat.around(p, c, radius)) {
            float d = dmg;
            if (scale) { // Red MAX: 30 close -> 7 at the edge [wiki]
                double k = Math.min(1.0, e.position().distanceTo(c) / radius);
                d = (float) (30 - 23 * k);
            }
            if (Combat.hit(p, e, d, scale ? Combat.UNBLOCKABLE : Combat.SEMI, true)) {
                Vec3 a = e.position().subtract(c);
                a = new Vec3(a.x, 0, a.z);
                a = a.lengthSqr() < 1.0E-4 ? Combat.flat(p) : a.normalize();
                Combat.knock(e, a.x * force, 0.6, a.z * force);
            }
        }
    }

    /** Rapid Punches - 17.25 unblockable, 15 s. [wiki] */
    private static void rapidPunches(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "rapid_punches", true, 6, 30);
        LivingEntity[] tg = {null};
        run(p, st, c, 30, t -> {
            if (t == 6) {
                List<LivingEntity> ls = Combat.line(p, 16, 2.0);
                if (ls.isEmpty()) {
                    cancelCast(st);
                    return;
                }
                tg[0] = ls.get(0);
                Vec3 dir = Combat.away(p, tg[0]);
                Vec3 dest = tg[0].position().subtract(dir.scale(1.7));
                Vec3 delta = dest.subtract(p.position());
                if (l.noCollision(p, p.getBoundingBox().move(delta))) {
                    Fx.dust(l, p.position().add(0, 1, 0), 0.8f, 0.9f, 1f, 2f, 20, 0.4);
                    p.teleportTo(dest.x, dest.y, dest.z);
                }
                Fx.sfx(l, dest, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.4f);
            }
            if (tg[0] != null && t >= 8 && t <= 24 && t % 2 == 0 && tg[0].isAlive() && tg[0].distanceTo(p) < 5) {
                p.swing(InteractionHand.MAIN_HAND, true);
                if (Combat.hit(p, tg[0], 17.25f / 9f, Combat.UNBLOCKABLE, true)) {
                    Combat.knock(tg[0], t == 24 ? Combat.away(p, tg[0]).scale(0.9).add(0, 0.3, 0) : Vec3.ZERO);
                    if (t == 24) {
                        st.lastRapidTarget = tg[0].getUUID();
                        st.lastRapidAt = now(p);
                    }
                    Fx.dust(l, Combat.mid(tg[0]), 0.8f, 0.9f, 1f, 1.2f, 6, 0.3);
                    Fx.sfx(l, Combat.mid(tg[0]), SoundEvents.PLAYER_ATTACK_WEAK, 1f, 1.3f);
                }
            }
        });
    }

    /** Twofold Kick - 10 (6 + 4), 18 s, semi-blockable. [wiki] */
    private static void twofoldKick(ServerPlayer p, JState st) {
        Cast c = begin(p, st, "twofold_kick", true, 6, 26);
        LivingEntity[] tg = {null};
        run(p, st, c, 26, t -> {
            if (t == 6) {
                p.setDeltaMovement(Combat.flat(p).scale(0.9).add(0, 0.1, 0));
                p.hurtMarked = true;
                p.swing(InteractionHand.MAIN_HAND, true);
            }
            if (t == 9) {
                List<LivingEntity> ls = Combat.line(p, 3.6, 1.0);
                if (!ls.isEmpty() && Combat.hit(p, ls.get(0), 6f, Combat.SEMI, true)) {
                    tg[0] = ls.get(0);
                    Vec3 a = Combat.away(p, tg[0]);
                    Combat.knock(tg[0], a.x * 0.5, 0.3, a.z * 0.5);
                    impactFx(p, tg[0], 1f, 1f, 1f);
                }
            }
            if (t == 16 && tg[0] != null && tg[0].isAlive() && tg[0].distanceTo(p) < 5) {
                p.setDeltaMovement(Combat.flat(p).scale(0.5));
                p.hurtMarked = true;
                p.swing(InteractionHand.MAIN_HAND, true);
                if (Combat.hit(p, tg[0], 4f, Combat.SEMI, true)) {
                    Vec3 a = Combat.away(p, tg[0]);
                    Combat.knock(tg[0], a.x * 1.2, 0.8, a.z * 1.2);
                    impactFx(p, tg[0], 1f, 1f, 1f);
                }
            }
        });
    }

    /** Limitless: target blink, aerial kick, and the Rapid Punches Face Grater follow-up. */
    private static void limitless(ServerPlayer p, JState st) {
        long n = now(p);
        if (!st.free(n) || st.cdLeft("limitless", n) > 0) return;
        ServerLevel l = lv(p);
        st.setCd("limitless", Kits.cd("limitless"), n);

        LivingEntity target = null;
        // Face Grater is only available briefly after Rapid Punches actually connects.
        if (st.lastRapidTarget != null && n - st.lastRapidAt <= 50) {
            net.minecraft.world.entity.Entity saved = l.getEntity(st.lastRapidTarget);
            if (saved instanceof LivingEntity living && living.isAlive() && living.distanceTo(p) <= 8.0) {
                target = living;
            }
        }
        if (target != null) {
            faceGrater(p, st);
            return;
        }

        // Standard Limitless locks onto the nearest target in the camera line.
        List<LivingEntity> targets = Combat.line(p, 24.0, 1.8);
        if (!targets.isEmpty()) {
            LivingEntity t = targets.get(0);
            Vec3 old = p.position();
            Vec3 away = Combat.away(p, t);
            Vec3 dest = t.position().subtract(away.scale(1.35));
            Fx.dust(l, old.add(0, 1, 0), 0.7f, 0.9f, 1f, 2f, 22, 0.45);
            Fx.sfx(l, old, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.3f);
            p.teleportTo(dest.x, dest.y, dest.z);
            p.fallDistance = 0;
            Fx.dust(l, dest.add(0, 1, 0), 0.7f, 0.9f, 1f, 2f, 22, 0.45);
            if (!t.onGround() || t.getDeltaMovement().y > 0.12) {
                Cast c = begin(p, st, "limitless_air_kick", true, 0, 12);
                run(p, st, c, 12, tick -> {
                    if (tick == 2 && t.isAlive() && t.distanceTo(p) < 5.0) {
                        p.swing(InteractionHand.MAIN_HAND, true);
                        if (Combat.hit(p, t, 8f, Combat.UNBLOCKABLE, true)) {
                            Vec3 knock = Combat.away(p, t);
                            Combat.knock(t, knock.x * 0.65, -0.95, knock.z * 0.65);
                            stun(t, 10);
                            Fx.dust(l, Combat.mid(t), 0.7f, 0.9f, 1f, 2f, 20, 0.4);
                            Fx.sfx(l, Combat.mid(t), SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 1.0f);
                        }
                    }
                });
            }
            return;
        }

        // No target: retain a short forward blink rather than wasting the input.
        Vec3 s = p.getEyePosition();
        Vec3 e = s.add(p.getLookAngle().scale(14.0));
        BlockHitResult r = l.clip(new ClipContext(s, e, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 dest = r.getType() == HitResult.Type.MISS
                ? e.subtract(0, p.getEyeHeight(), 0)
                : r.getLocation().subtract(p.getLookAngle().scale(0.7)).subtract(0, p.getEyeHeight(), 0);
        Fx.dust(l, p.position().add(0, 1, 0), 0.7f, 0.9f, 1f, 2f, 25, 0.5);
        Fx.sfx(l, p.position(), SoundEvents.ENDERMAN_TELEPORT, 1f, 1.3f);
        p.teleportTo(dest.x, dest.y, dest.z);
        p.fallDistance = 0;
        Fx.dust(l, dest.add(0, 1, 0), 0.7f, 0.9f, 1f, 2f, 25, 0.5);
    }

    /** Rapid Punches follow-up: drag the grabbed target, then throw them forward. */
    private static void faceGrater(ServerPlayer p, JState st) {
        long n = now(p);
        if (st.cdLeft("limitless", n) > 0 || st.lastRapidTarget == null) return;
        net.minecraft.world.entity.Entity entity = lv(p).getEntity(st.lastRapidTarget);
        if (!(entity instanceof LivingEntity grabbed) || !grabbed.isAlive() || grabbed.distanceTo(p) > 8.0) {
            st.lastRapidTarget = null;
            return;
        }
        st.setCd("limitless", Kits.cd("limitless"), n);
        Vec3 away = Combat.away(p, grabbed);
        Vec3 behind = grabbed.position().subtract(away.scale(1.0));
        Fx.dust(lv(p), p.position().add(0, 1, 0), 0.7f, 0.9f, 1f, 2f, 22, 0.45);
        Fx.sfx(lv(p), p.position(), SoundEvents.ENDERMAN_TELEPORT, 1f, 1.5f);
        p.teleportTo(behind.x, behind.y, behind.z);
        Cast c = begin(p, st, "face_grater", true, 0, 24);
        run(p, st, c, 24, t -> {
            if (!grabbed.isAlive()) return;
            if (t <= 14) {
                Combat.place(grabbed, p.position().add(Combat.flat(p).scale(1.35)));
                if (t == 2 || t == 5 || t == 8 || t == 11 || t == 14) {
                    p.swing(InteractionHand.MAIN_HAND, true);
                    Fx.dust(lv(p), Combat.mid(grabbed), 0.75f, 0.85f, 1f, 1.0f, 5, 0.2);
                    Combat.hit(p, grabbed, 7.52f / 5f, Combat.BLOCK_360, true);
                }
            }
            if (t == 18) {
                p.swing(InteractionHand.MAIN_HAND, true);
                if (Combat.hit(p, grabbed, 2.5f, Combat.BLOCK_360, true)) {
                    Vec3 launch = Combat.flat(p);
                    Combat.knock(grabbed, launch.x * 1.15, 0.42, launch.z * 1.15);
                    Fx.sfx(lv(p), Combat.mid(grabbed), SoundEvents.PLAYER_ATTACK_CRIT, 1.1f, 1.15f);
                }
                st.lastRapidTarget = null;
            }
        });
    }

    /** Reversal Red's special variant: phase behind the target and detonate Red at point blank. */
    private static void reversalRedSpecial(ServerPlayer p, JState st) {
        long n = now(p);
        List<LivingEntity> targets = Combat.line(p, 35.0, 1.4);
        if (targets.isEmpty()) return;
        LivingEntity target = targets.get(0);
        cancelCast(st);
        st.setCd("limitless", Kits.cd("limitless"), n);
        Vec3 away = Combat.away(p, target);
        Vec3 dest = target.position().subtract(away.scale(1.1));
        Fx.dust(lv(p), p.position().add(0, 1, 0), 1f, 0.12f, 0.12f, 2.2f, 28, 0.5);
        Fx.sfx(lv(p), p.position(), SoundEvents.ENDERMAN_TELEPORT, 1.2f, 1.4f);
        p.teleportTo(dest.x, dest.y, dest.z);
        p.fallDistance = 0;
        Vec3 point = Combat.mid(target);
        Fx.dust(lv(p), point, 1f, 0.08f, 0.08f, 2.8f, 50, 0.55);
        redImpact(p, point, 3.0, 12.5f, 2.0, false);
        st.lockUntil = n + 12;
    }

    /** Lapse Blue MAX - 44 (2.2 per tick x 20), 17 s, unblockable. [wiki] */
    private static void lapseBlueMax(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "lapse_blue_max", true, 12, 14);
        Vec3[] center = {null};
        run(p, st, c, 12 + 20, t -> {
            if (t < 12) {
                Fx.dust(l, p.getEyePosition().add(p.getLookAngle().scale(2.0)), 0.15f, 0.4f, 1f, 1.5f + t * 0.1f, 6, 0.3);
            }
            if (t == 12) {
                center[0] = Combat.rayPoint(p, 8.0);
                Fx.sfx(l, center[0], SoundEvents.ENDERMAN_TELEPORT, 1.5f, 0.4f);
            }
            if (t > 12 && center[0] != null) {
                Vec3 cp = center[0];
                Fx.dust(l, cp, 0.15f, 0.4f, 1f, 3f, 30, 0.8);
                l.sendParticles(ParticleTypes.ELECTRIC_SPARK, cp.x, cp.y, cp.z, 6, 1.5, 1.5, 1.5, 0.1);
                for (LivingEntity e : Combat.around(p, cp, 9.0)) {
                    Vec3 to = cp.subtract(Combat.mid(e));
                    double d = to.length();
                    if (d > 0.05) Combat.knock(e, to.normalize().scale(Math.min(0.9, d * 0.3)));
                    Combat.hit(p, e, 2.2f, Combat.UNBLOCKABLE, true);
                }
            }
        });
    }

    /** Reversal Red MAX - 30 close to 7 far, 10 s, unblockable. [wiki] */
    private static void reversalRedMax(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "reversal_red_max", true, 10, 24);
        run(p, st, c, 24, t -> {
            if (t < 10) Fx.dust(l, p.getEyePosition().add(p.getLookAngle().scale(1.8)), 1f, 0.1f, 0.1f, 2.4f, 8, 0.3);
            if (t == 10) {
                Vec3 s = p.getEyePosition();
                Vec3 end = Combat.rayPoint(p, 30.0);
                List<LivingEntity> hit = Combat.line(p, s.distanceTo(end), 1.2);
                Vec3 impact = hit.isEmpty() ? end : Combat.mid(hit.get(0));
                Combat.trail(s, impact, 0.6, v -> Fx.dust(l, v, 1f, 0.1f, 0.1f, 1.6f, 3, 0.1));
                redImpact(p, impact, 9.0, 0f, 2.6, true);
            }
        });
    }

    /** Hollow Purple - 70, 40 s, uninterruptible, unblockable. [wiki] */
    private static void hollowPurple(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "hollow_purple", false, 40, 60);
        run(p, st, c, 40, t -> {
            Vec3 look = p.getLookAngle();
            Vec3 ctr = p.getEyePosition().add(look.scale(2.5));
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            double off = Math.max(0.0, 1.0 - t / 36.0) * 1.8;
            Fx.dust(l, ctr.add(right.scale(off)), 0.15f, 0.4f, 1f, 2f, 5, 0.15);
            Fx.dust(l, ctr.subtract(right.scale(off)), 1f, 0.1f, 0.1f, 2f, 5, 0.15);
            if (t == 20) Fx.sfx(l, ctr, SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
            if (t == 40) firePurple(p, ctr, look);
        });
    }

    private static void firePurple(ServerPlayer p, Vec3 origin, Vec3 dir) {
        ServerLevel l = lv(p);
        Fx.sfx(l, origin, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 0.7f);
        Vec3[] pos = {origin};
        int[] t = {0};
        java.util.Set<java.util.UUID> done = new java.util.HashSet<>();
        Tasks.add(() -> {
            t[0]++;
            pos[0] = pos[0].add(dir.scale(1.5));
            Vec3 c = pos[0];
            if (!l.hasChunkAt(BlockPos.containing(c))) return true;
            Fx.dust(l, c, 0.6f, 0.1f, 1f, 3.5f, 40, 1.6);
            Fx.dust(l, c, 1f, 1f, 1f, 2f, 10, 0.6);
            for (LivingEntity e : Combat.around(p, c, 3.5)) {
                if (done.add(e.getUUID()) && Combat.hit(p, e, 70f, Combat.UNBLOCKABLE, false)) {
                    Combat.knock(e, dir.x * 1.5, 0.3, dir.z * 1.5);
                }
            }
            if (l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                BlockPos cp = BlockPos.containing(c);
                int r = 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx * dx + dy * dy + dz * dz > r * r + 1) continue;
                            BlockPos bp = cp.offset(dx, dy, dz);
                            BlockState bs = l.getBlockState(bp);
                            if (!bs.isAir() && bs.getDestroySpeed(l, bp) >= 0f) l.removeBlock(bp, false);
                        }
                    }
                }
            }
            return t[0] >= 28;
        });
    }

    /** Infinite Void - domain, 120 s. Everyone in range is stunned (damage not published). [wiki] */
    private static void infiniteVoid(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "infinite_void", false, 20, 20);
        run(p, st, c, 180, t -> {
            if (t < 20) Fx.dust(l, p.position().add(0, 1, 0), 1f, 1f, 1f, 2f, 10, 1.5);
            if (t == 20) {
                Fx.sfx(l, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 0.6f);
                for (LivingEntity e : Combat.around(p, p.position(), 24.0)) stun(e, 160);
            }
            if (t > 20 && t % 4 == 0) {
                for (LivingEntity e : Combat.around(p, p.position(), 24.0)) {
                    Fx.dust(l, Combat.mid(e), 1f, 1f, 1f, 1.2f, 6, 0.5);
                    Fx.dust(l, Combat.mid(e), 0f, 0f, 0f, 1.5f, 6, 0.5);
                }
            }
        });
    }

    // =====================================================================
    //  VESSEL
    // =====================================================================

    /** Cursed Strikes - 17.5 (4 grab + 6x1.75 + 3 kick), 14 s, blockable all sides. [wiki] */
    private static void cursedStrikes(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "cursed_strikes", true, 4, 40);
        st.bulletUntil = now(p) + 10;
        LivingEntity[] tg = {null};
        run(p, st, c, 40, t -> {
            if (t >= 2 && t <= 7) {
                p.setDeltaMovement(Combat.flat(p).scale(0.9));
                p.hurtMarked = true;
            }
            if (t == 7) {
                List<LivingEntity> ls = Combat.line(p, 3.5, 1.0);
                if (ls.isEmpty() || !Combat.hit(p, ls.get(0), 4f, Combat.BLOCK_360, true)) {
                    cancelCast(st);
                    return;
                }
                tg[0] = ls.get(0);
                impactFx(p, tg[0], 1f, 0.4f, 0.1f);
            }
            if (tg[0] != null && t >= 9 && t <= 19 && t % 2 == 1 && tg[0].isAlive()) {
                p.swing(InteractionHand.MAIN_HAND, true);
                if (Combat.hit(p, tg[0], 1.75f, Combat.BLOCK_360, true)) {
                    Combat.knock(tg[0], Vec3.ZERO);
                    Fx.dust(l, Combat.mid(tg[0]), 1f, 0.4f, 0.1f, 1.2f, 5, 0.3);
                    Fx.sfx(l, Combat.mid(tg[0]), SoundEvents.PLAYER_ATTACK_WEAK, 1f, 1.2f);
                } else {
                    cancelCast(st);
                }
            }
            if (t == 23 && tg[0] != null && tg[0].isAlive() && tg[0].distanceTo(p) < 5) {
                if (Combat.hit(p, tg[0], 3f, Combat.BLOCK_360, true)) {
                    Vec3 a = Combat.away(p, tg[0]);
                    Combat.knock(tg[0], a.x * 1.0, 0.3, a.z * 1.0);
                    stun(tg[0], 20);
                    impactFx(p, tg[0], 1f, 0.4f, 0.1f);
                }
            }
        });
    }

    /** Crushing Blow - 12 (6 per slam x2, 3 shockwave on miss), 15 s, unblockable grab. [wiki] */
    private static void crushingBlow(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "crushing_blow", true, 8, 34);
        LivingEntity[] tg = {null};
        run(p, st, c, 34, t -> {
            if (t == 8) {
                List<LivingEntity> ls = Combat.line(p, 4.0, 1.0);
                if (ls.isEmpty()) {
                    Vec3 ctr = p.position();
                    l.sendParticles(ParticleTypes.EXPLOSION, ctr.x, ctr.y + 0.5, ctr.z, 6, 1.5, 0.2, 1.5, 0);
                    Fx.sfx(l, ctr, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.5f);
                    for (LivingEntity e : Combat.around(p, ctr, 4.0)) {
                        if (Combat.hit(p, e, 3f, Combat.UNBLOCKABLE, true)) {
                            Vec3 a = Combat.away(p, e);
                            Combat.knock(e, a.x * 0.8, 0.4, a.z * 0.8);
                        }
                    }
                    cancelCast(st);
                    return;
                }
                tg[0] = ls.get(0);
            }
            if (tg[0] != null && t >= 8 && t < 26 && tg[0].isAlive()) {
                Combat.place(tg[0], p.position().add(Combat.flat(p).scale(1.8)));
            }
            if (tg[0] != null && (t == 12 || t == 22) && tg[0].isAlive()) {
                if (Combat.hit(p, tg[0], 6f, Combat.UNBLOCKABLE, true)) {
                    Vec3 g = tg[0].position();
                    l.sendParticles(ParticleTypes.EXPLOSION, g.x, g.y + 0.2, g.z, 3, 0.4, 0.1, 0.4, 0);
                    Fx.sfx(l, g, SoundEvents.ANVIL_LAND, 1f, 0.8f);
                }
            }
            if (tg[0] != null && t == 26 && tg[0].isAlive()) {
                Vec3 a = Combat.away(p, tg[0]);
                Combat.knock(tg[0], a.x * 1.6, 0.6, a.z * 1.6);
            }
        });
    }

    /**
     * Divergent Fist - 10 (5 + delayed 5), 18 s, semi-blockable. [wiki]
     * A white flash cue appears during the pullback; pressing the key again inside the window fires a Black Flash
     * (10 dmg, or 20 if it interrupts an action). Pressing outside the window locks Black Flash out. [wiki]
     */
    private static void divergentFist(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "divergent_fist", true, 14, 44);
        Fx.sfx(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        LivingEntity[] tg = {null};
        run(p, st, c, 44, t -> {
            Vec3 fist = p.getEyePosition().add(p.getLookAngle().scale(1.1)).subtract(0, 0.4, 0);
            long dt = now(p) - c.start;
            boolean cue = dt >= BF_START && dt <= BF_END && !c.bf && !c.bfLocked;
            if (cue) {
                Fx.dust(l, fist, 1f, 1f, 1f, 2.5f, 8, 0.15);
                if (dt == BF_START) Fx.sfx(l, fist, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.8f);
            }
            if (c.bf) {
                if (t == c.bfAt + 3) fireBlackFlash(p, st);
                return;
            }
            if (t == 14) {
                p.swing(InteractionHand.MAIN_HAND, true);
                p.setDeltaMovement(Combat.flat(p).scale(0.5));
                p.hurtMarked = true;
                List<LivingEntity> ls = Combat.line(p, 3.8, 1.0);
                if (!ls.isEmpty() && Combat.hit(p, ls.get(0), 5f, Combat.SEMI, true)) {
                    tg[0] = ls.get(0);
                    Combat.knock(tg[0], Vec3.ZERO);
                    impactFx(p, tg[0], 1f, 0.5f, 0.1f);
                }
            }
            if (t == 24 && tg[0] != null && tg[0].isAlive()) { // delayed cursed-energy impact
                if (Combat.hit(p, tg[0], 5f, Combat.SEMI, true)) {
                    Vec3 a = Combat.away(p, tg[0]);
                    Combat.knock(tg[0], a.x * 1.3, 0.4, a.z * 1.3);
                    Fx.dust(l, Combat.mid(tg[0]), 1f, 0.5f, 0.1f, 2.5f, 25, 0.5);
                    Fx.sfx(l, Combat.mid(tg[0]), SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
                }
            }
        });
    }

    private static void blackFlashPress(ServerPlayer p, JState st, long n) {
        Cast c = st.cast;
        if (c == null || c.bf || c.bfLocked) return;
        long dt = n - c.start;
        if (dt >= BF_START && dt <= BF_END) {
            c.bf = true;
            c.bfAt = dt + 1; // run() counts t = dt + 1 on this tick
        } else {
            c.bfLocked = true; // too early / too late: no Black Flash this cast
            p.displayClientMessage(Component.literal("Mistimed"), true);
        }
    }

    private static void fireBlackFlash(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        p.swing(InteractionHand.MAIN_HAND, true);
        p.setDeltaMovement(Combat.flat(p).scale(0.6));
        p.hurtMarked = true;
        List<LivingEntity> ls = Combat.line(p, 4.5, 1.0);
        if (ls.isEmpty()) {
            Fx.sfx(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
            return;
        }
        LivingEntity t = ls.get(0);
        boolean busy = isBusy(t);
        if (Combat.hit(p, t, busy ? 20f : 10f, Combat.SEMI, true)) {
            Vec3 a = Combat.away(p, t);
            Combat.knock(t, a.x * 2.2, 0.5, a.z * 2.2);
            Vec3 c = Combat.mid(t);
            Fx.dust(l, c, 0.05f, 0.05f, 0.05f, 3.5f, 50, 0.9);
            Fx.dust(l, c, 1f, 0.1f, 0.1f, 2.5f, 40, 0.9);
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.8, 0.8, 0.8, 0.4);
            Fx.sfx(l, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5f, 1.5f);
            p.displayClientMessage(Component.literal(busy ? "BLACK FLASH - INTERRUPT" : "BLACK FLASH"), true);
            st.addAwake(8f); // Black Flash charges awakening fastest [wiki]
        }
    }

    /** Manji Kick - counter, 0.6 s window, 8.5 dmg, 20 s. [wiki] */
    private static void manjiKick(ServerPlayer p, JState st) {
        long n = now(p);
        Cast c = begin(p, st, "manji_kick", true, 0, 14);
        st.counterUntil = n + 12;
        Fx.dust(lv(p), p.position().add(0, 1, 0), 0.2f, 1f, 0.8f, 1.6f, 12, 0.5);
        run(p, st, c, 14, t -> {
            if (t < 12) Fx.dust(lv(p), p.position().add(0, 1, 0), 0.2f, 1f, 0.8f, 1.0f, 2, 0.4);
        });
    }

    /** Called from the damage event when a Manji Kick counter catches an attacker. */
    public static void manjiCounter(ServerPlayer p, JState st, LivingEntity attacker) {
        ServerLevel l = lv(p);
        st.counterUntil = 0;
        p.swing(InteractionHand.MAIN_HAND, true);
        if (attacker.position().distanceTo(p.position()) <= 8) {
            if (Combat.hit(p, attacker, 8.5f, Combat.UNBLOCKABLE, false)) {
                Vec3 a = Combat.away(p, attacker);
                Combat.knock(attacker, a.x * 1.0, 0.9, a.z * 1.0);
            }
        }
        Fx.dust(l, p.position().add(0, 1, 0), 0.2f, 1f, 0.8f, 2.5f, 25, 0.6);
        Fx.sfx(l, p.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.3f, 0.6f);
    }

    /** Cleave - 40% of current HP (min 10), 12 s, unblockable. [wiki] */
    private static void cleave(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "cleave", true, 8, 24);
        run(p, st, c, 24, t -> {
            if (t == 8) {
                List<LivingEntity> ls = Combat.line(p, 4.2, 1.0);
                if (ls.isEmpty()) return;
                LivingEntity tg = ls.get(0);
                float d = Math.max(10f, tg.getHealth() * 0.4f);
                if (Combat.hit(p, tg, d, Combat.UNBLOCKABLE, true)) {
                    Vec3 a = Combat.away(p, tg);
                    Combat.knock(tg, a.x * 1.0, 0.9, a.z * 1.0);
                    Vec3 m = Combat.mid(tg);
                    l.sendParticles(ParticleTypes.SWEEP_ATTACK, m.x, m.y, m.z, 3, 0.5, 0.5, 0.5, 0);
                    Fx.dust(l, m, 1f, 0.1f, 0.1f, 2f, 25, 0.5);
                    Fx.sfx(l, m, SoundEvents.PLAYER_ATTACK_CRIT, 1.3f, 0.6f);
                }
            }
        });
    }

    /** Dismantle - 17.5 total (1.75 per tick), 13 s, 30-stud radius, blockers take reduced damage. [wiki] */
    private static void dismantle(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "dismantle", true, 8, 30);
        run(p, st, c, 30, t -> {
            if (t >= 8 && t <= 26 && t % 2 == 0) {
                for (LivingEntity e : Combat.around(p, p.position(), 30 * Combat.STUD)) {
                    Vec3 m = Combat.mid(e);
                    sweepFx(p, m);
                    Fx.dust(l, m, 1f, 0.1f, 0.1f, 1.3f, 6, 0.4);
                    Combat.hit(p, e, 1.75f, Combat.SEMI, true);
                }
                Fx.sfx(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
            }
        });
    }

    /** Open - 30, 40 s, unblockable fire arrow that lifts enemies in a flame pillar. [wiki] */
    private static void open(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "open", true, 24, 40);
        run(p, st, c, 40, t -> {
            if (t < 24) {
                Vec3 f = p.getEyePosition().add(p.getLookAngle().scale(1.5));
                l.sendParticles(ParticleTypes.FLAME, f.x, f.y, f.z, 3, 0.15, 0.15, 0.15, 0.01);
            }
            if (t == 24) {
                Vec3 s = p.getEyePosition();
                Vec3 end = Combat.rayPoint(p, 40.0);
                List<LivingEntity> ls = Combat.line(p, s.distanceTo(end), 0.9);
                Vec3 impact = ls.isEmpty() ? end : Combat.mid(ls.get(0));
                Combat.trail(s, impact, 0.7, v -> l.sendParticles(ParticleTypes.FLAME, v.x, v.y, v.z, 2, 0.05, 0.05, 0.05, 0.01));
                Fx.sfx(l, impact, SoundEvents.FIRECHARGE_USE, 1.5f, 0.7f);
                Fx.sfx(l, impact, SoundEvents.GENERIC_EXPLODE, 1f, 1.3f);
                for (LivingEntity e : Combat.around(p, impact, 4.0)) {
                    if (Combat.hit(p, e, 30f, Combat.UNBLOCKABLE, true)) e.setRemainingFireTicks(100);
                }
                int[] k = {0};
                Tasks.add(() -> {
                    k[0]++;
                    for (int i = 0; i < 4; i++) {
                        l.sendParticles(ParticleTypes.FLAME, impact.x, impact.y + k[0] * 0.3 + i * 0.4, impact.z, 12, 1.2, 0.2, 1.2, 0.03);
                    }
                    for (LivingEntity e : Combat.around(p, impact, 4.0)) {
                        Vec3 v = e.getDeltaMovement();
                        e.setDeltaMovement(v.x * 0.5, 0.45, v.z * 0.5);
                        e.hurtMarked = true;
                    }
                    return k[0] >= 14;
                });
            }
        });
    }

    /** Rush - 25 (5 impact + 15 knee + 5 slam), 15 s. [wiki] */
    private static void rush(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "rush", true, 4, 40);
        LivingEntity[] tg = {null};
        int[] hitAt = {-1};
        run(p, st, c, 40, t -> {
            if (t >= 4 && t <= 14 && tg[0] == null) {
                p.setDeltaMovement(Combat.flat(p).scale(1.6));
                p.hurtMarked = true;
                l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 4, 0.3, 0.1, 0.3, 0.02);
                List<LivingEntity> ls = Combat.line(p, 2.2, 1.0);
                if (!ls.isEmpty() && Combat.hit(p, ls.get(0), 5f, Combat.BLOCKABLE, true)) {
                    tg[0] = ls.get(0);
                    hitAt[0] = t;
                    Combat.knock(tg[0], Vec3.ZERO);
                    impactFx(p, tg[0], 1f, 0.3f, 0.1f);
                }
            }
            if (tg[0] != null && tg[0].isAlive()) {
                if (t == hitAt[0] + 4) {
                    p.swing(InteractionHand.MAIN_HAND, true);
                    if (Combat.hit(p, tg[0], 15f, Combat.SEMI, true)) {
                        Combat.knock(tg[0], 0, 1.0, 0);
                        impactFx(p, tg[0], 1f, 0.3f, 0.1f);
                    }
                }
                if (t == hitAt[0] + 12) {
                    if (Combat.hit(p, tg[0], 5f, Combat.UNBLOCKABLE, true)) {
                        Combat.knock(tg[0], 0, -1.8, 0);
                        Fx.sfx(l, tg[0].position(), SoundEvents.ANVIL_LAND, 1f, 0.8f);
                    }
                }
            }
        });
    }

    /** Malevolent Shrine - domain: 78 sure-hit slashes x 2 dmg over 12 s, 120 s cooldown. [wiki] */
    private static void malevolentShrine(ServerPlayer p, JState st) {
        ServerLevel l = lv(p);
        Cast c = begin(p, st, "malevolent_shrine", false, 30, 30);
        run(p, st, c, 270, t -> {
            if (t < 30) Fx.dust(l, p.position().add(0, 1, 0), 0.8f, 0.05f, 0.05f, 2f, 10, 1.5);
            if (t == 30) Fx.sfx(l, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 0.5f);
            if (t >= 30 && t < 264 && (t - 30) % 3 == 0) {
                for (LivingEntity e : Combat.around(p, p.position(), 12.0)) {
                    Vec3 m = Combat.mid(e);
                    sweepFx(p, m);
                    Fx.dust(l, m, 1f, 0.1f, 0.1f, 1.2f, 4, 0.5);
                    Combat.hit(p, e, 2f, Combat.SEMI, false);
                }
            }
        });
    }


    // Toji / Hired Killer: three cursed tools and seven follow-up variants.
    private static LivingEntity tojiTarget(ServerPlayer p, JState st) {
        if (st.tojiTarget == null) return null;
        net.minecraft.world.entity.Entity e = lv(p).getEntity(st.tojiTarget);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    private static void tojiSwap(ServerPlayer p, JState st, long n) {
        if (st.cdLeft("hidden_inventory", n) > 0) return;
        if (st.cast != null && st.cast.id.equals("flourish") && n <= st.tojiVariantUntil) { tojiGreed(p, st); return; }
        st.tojiWeapon = (st.tojiWeapon + 1) % 3;
        st.setCd("hidden_inventory", 100, n);
        st.m1 = 0; st.m1Ready = n + 3;
        Fx.sfx(lv(p), p.position(), SoundEvents.ITEM_PICKUP, 1.2f, 0.7f + st.tojiWeapon * 0.2f);
        Fx.dust(lv(p), p.position().add(0,1,0), 0.8f,0.75f,0.55f,1.5f,24,0.5);
        p.displayClientMessage(Component.literal(new String[]{"SPLIT SOUL KATANA","INVERTED SPEAR OF HEAVEN","CHAIN OF A THOUSAND MILES"}[st.tojiWeapon]), true);
    }

    private static boolean tojiVariant(ServerPlayer p, JState st, String id, long n) {
        if (st.cast == null || !st.cast.live(p) || n > st.tojiVariantUntil) return false;
        if (id.equals("flourish") && st.cast.id.equals("flourish")) tojiWrath(p,st);
        else if (id.equals("transcend") && st.cast.id.equals("transcend")) tojiAscend(p,st);
        else if (id.equals("rupture") && st.cast.id.equals("rupture")) tojiParadise(p,st);
        else if (id.equals("whiplash") && st.cast.id.equals("whiplash")) tojiTwirling(p,st);
        else return false;
        return true;
    }

    private static void tojiFlourish(ServerPlayer p,JState st) {
        long n=now(p); ServerLevel l=lv(p); Cast c=begin(p,st,"flourish",true,7,20);
        st.tojiVariantUntil=n+28; st.tojiTarget=null;
        run(p,st,c,20,t->{
            Vec3 at=p.getEyePosition().add(p.getLookAngle().scale(1.2));
            if(t<8) Fx.dust(l,at,0.75f,0.8f,0.9f,1.2f,6,0.25);
            if(t==8){List<LivingEntity>a=Combat.line(p,3.8,1.2);if(!a.isEmpty()&&Combat.hit(p,a.get(0),5f,Combat.UNBLOCKABLE,true)){st.tojiTarget=a.get(0).getUUID();stun(a.get(0),12);Combat.knock(a.get(0),Combat.away(p,a.get(0)).scale(0.45));Fx.dust(l,Combat.mid(a.get(0)),0.8f,0.85f,1f,2f,22,0.5);}}
        });
    }

    private static void tojiWrath(ServerPlayer p,JState st) {
        LivingEntity e=tojiTarget(p,st); if(e==null||e.distanceTo(p)>8)return;
        st.tojiVariantUntil=0; Cast c=begin(p,st,"the_wrath",false,0,22);
        run(p,st,c,22,t->{if((t==2||t==7||t==12)&&e.isAlive()&&e.distanceTo(p)<8){Combat.hit(p,e,t==12?3f:2f,Combat.UNBLOCKABLE,true);p.swing(InteractionHand.MAIN_HAND,true);if(t==12)Combat.knock(e,Combat.away(p,e).scale(1));Fx.dust(lv(p),Combat.mid(e),0.8f,0.85f,1f,1.5f,12,0.3);}});
    }

    private static void tojiGreed(ServerPlayer p,JState st) {
        LivingEntity e=tojiTarget(p,st);if(e==null||e.distanceTo(p)>8)return;st.tojiVariantUntil=0;st.tojiWeapon=1;
        Cast c=begin(p,st,"the_greed",false,0,24);
        run(p,st,c,24,t->{if(t==2&&e.isAlive()){Combat.hit(p,e,3f,Combat.UNBLOCKABLE,true);Combat.knock(e,0,0.8,0);}if(t>=6&&t<=18&&t%3==0&&e.isAlive()&&e.distanceTo(p)<8){Combat.hit(p,e,1f,Combat.UNBLOCKABLE,true);Fx.dust(lv(p),Combat.mid(e),0.8f,0.9f,1f,1.2f,8,0.25);}if(t==21&&e.isAlive())Combat.knock(e,Combat.away(p,e).scale(0.8).add(0,-0.7,0));});
    }

    private static void tojiPride(ServerPlayer p,JState st) {
        LivingEntity e=tojiTarget(p,st);if(e==null||e.distanceTo(p)>9)return;st.tojiVariantUntil=0;st.tojiWeapon=2;st.blocking=false;
        Cast c=begin(p,st,"the_pride",false,0,24);
        run(p,st,c,24,t->{if(t==2&&e.isAlive()){Combat.hit(p,e,3f,Combat.UNBLOCKABLE,true);Combat.knock(e,0,0.6,0);}if(t==12&&e.isAlive()){Combat.hit(p,e,6f,Combat.UNBLOCKABLE,true);Combat.knock(e,0,-1.2,0);Fx.sfx(lv(p),Combat.mid(e),SoundEvents.ANVIL_LAND,1f,0.8f);}});
    }

    private static void tojiDebilitate(ServerPlayer p,JState st) {
        Cast c=begin(p,st,"debilitate",true,4,32);LivingEntity[] e={null};
        run(p,st,c,32,t->{if(t>=4&&t<=10&&e[0]==null){p.setDeltaMovement(Combat.flat(p).scale(1.35));p.hurtMarked=true;List<LivingEntity>a=Combat.line(p,2.8,1.1);if(!a.isEmpty()&&Combat.hit(p,a.get(0),4f,Combat.BLOCKABLE,true)){e[0]=a.get(0);Combat.knock(e[0],0,0.65,0);}}if(e[0]!=null&&e[0].isAlive()){if(t==15||t==20)Combat.hit(p,e[0],4.5f,Combat.BLOCKABLE,true);if(t==27){Combat.hit(p,e[0],4f,Combat.BLOCKABLE,true);Combat.knock(e[0],0,-1.3,0);Fx.sfx(lv(p),e[0].position(),SoundEvents.ANVIL_LAND,1f,1f);}}});
    }

    private static void tojiTranscend(ServerPlayer p,JState st) {
        Cast c=begin(p,st,"transcend",true,3,18);st.tojiVariantUntil=now(p)+22;st.tojiTarget=null;
        run(p,st,c,18,t->{if(t<=7){p.setDeltaMovement(Combat.flat(p).scale(1.55).add(0,0.04,0));p.hurtMarked=true;lv(p).sendParticles(ParticleTypes.ELECTRIC_SPARK,p.getX(),p.getY()+1,p.getZ(),5,0.25,0.35,0.25,0.02);}if(t==8){List<LivingEntity>a=Combat.line(p,3.4,1.2);if(!a.isEmpty()&&Combat.hit(p,a.get(0),12f,Combat.BLOCKABLE,true)){st.tojiTarget=a.get(0).getUUID();Combat.knock(a.get(0),Combat.away(p,a.get(0)).scale(0.7));}}});
    }

    private static void tojiAscend(ServerPlayer p,JState st) {
        LivingEntity e=tojiTarget(p,st);if(e==null||e.distanceTo(p)>8)return;st.tojiVariantUntil=0;Cast c=begin(p,st,"ascend",false,0,20);
        run(p,st,c,20,t->{if(t==3&&e.isAlive()){Combat.hit(p,e,5f,Combat.UNBLOCKABLE,true);Combat.knock(e,0,1.15,0);Fx.dust(lv(p),Combat.mid(e),0.8f,0.9f,1f,2f,25,0.5);}if(t==10&&e.isAlive())Combat.hit(p,e,4f,Combat.UNBLOCKABLE,true);});
    }

    private static void tojiRupture(ServerPlayer p,JState st) {
        Cast c=begin(p,st,"rupture",true,6,26);st.tojiVariantUntil=now(p)+34;st.tojiTarget=null;
        run(p,st,c,26,t->{if(t==6){List<LivingEntity>a=Combat.line(p,4.5,1.2);if(!a.isEmpty()&&Combat.hit(p,a.get(0),8f,Combat.UNBLOCKABLE,true)){st.tojiTarget=a.get(0).getUUID();Combat.knock(a.get(0),0,1,0);}}});
    }

    private static void tojiParadise(ServerPlayer p,JState st) {
        LivingEntity e=tojiTarget(p,st);if(e==null||e.distanceTo(p)>10)return;st.tojiVariantUntil=0;Cast c=begin(p,st,"paradise",false,0,22);
        run(p,st,c,22,t->{if(t==4&&e.isAlive()){Combat.hit(p,e,14f,Combat.UNBLOCKABLE,true);Combat.knock(e,Combat.away(p,e).scale(0.9).add(0,-1,0));Fx.sfx(lv(p),Combat.mid(e),SoundEvents.PLAYER_ATTACK_CRIT,1.3f,0.7f);}});
    }

    private static void tojiLasso(ServerPlayer p,JState st) {
        Cast c=begin(p,st,"lasso",true,7,28);st.tojiTarget=null;
        run(p,st,c,28,t->{Vec3 hand=p.getEyePosition().add(p.getLookAngle().scale(1.2));if(t<8)Fx.dust(lv(p),hand,0.75f,0.75f,0.65f,1.4f,6,0.3);if(t==8){List<LivingEntity>a=Combat.line(p,24,0.8);if(!a.isEmpty()&&Combat.hit(p,a.get(0),6f,Combat.BLOCKABLE,true)){st.tojiTarget=a.get(0).getUUID();Combat.knock(a.get(0),p.position().subtract(a.get(0).position()).normalize().scale(0.65));}}if(t==18){LivingEntity e=tojiTarget(p,st);if(e!=null&&e.distanceTo(p)<4){Combat.hit(p,e,6f,Combat.BLOCKABLE,true);Combat.knock(e,Combat.away(p,e).scale(0.8));}}});
    }

    private static void tojiWhiplash(ServerPlayer p,JState st) {
        Cast c=begin(p,st,"whiplash",true,5,23);st.tojiVariantUntil=now(p)+28;st.tojiTarget=null;
        run(p,st,c,23,t->{if(t==5||t==12){List<LivingEntity>a=Combat.line(p,9,1);if(!a.isEmpty()&&Combat.hit(p,a.get(0),2.5f,Combat.BLOCKABLE,true)){st.tojiTarget=a.get(0).getUUID();Combat.knock(a.get(0),Combat.away(p,a.get(0)).scale(0.25));Fx.dust(lv(p),Combat.mid(a.get(0)),0.75f,0.75f,0.65f,1.6f,12,0.35);}}});
    }

    private static void tojiTwirling(ServerPlayer p,JState st) {
        LivingEntity e=tojiTarget(p,st);st.tojiVariantUntil=0;Cast c=begin(p,st,"twirling_strike",false,0,26);
        run(p,st,c,26,t->{if(t>=3&&t<=18&&t%3==0&&e!=null&&e.isAlive()&&e.distanceTo(p)<10){Combat.hit(p,e,1.3f,Combat.UNBLOCKABLE,true);Fx.dust(lv(p),Combat.mid(e),0.8f,0.75f,0.55f,1.4f,8,0.3);}if(t==22&&e!=null&&e.isAlive())Combat.knock(e,Combat.away(p,e).scale(1.2));});
    }

}
