package com.example.jjsmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Combat {
    /** 1 stud = 0.28 blocks (Roblox). */
    public static final double STUD = 0.28;

    /** How a hit interacts with blocking. */
    public static final int UNBLOCKABLE = 0, BLOCKABLE = 1, SEMI = 2, BLOCK_360 = 3;

    public static final class Ctx {
        public int mode;
        public boolean counterable;
    }

    /** Context of the hit currently being applied (read by the damage event). */
    public static Ctx ctx;

    public static ServerLevel lv(ServerPlayer p) {
        return (ServerLevel) p.level();
    }

    public static long now(ServerPlayer p) {
        return p.level().getGameTime();
    }

    public static boolean hit(ServerPlayer atk, LivingEntity t, float dmg, int mode, boolean counterable) {
        if (t == atk || !t.isAlive()) return false;
        Ctx prev = ctx;
        Ctx c = new Ctx();
        c.mode = mode;
        c.counterable = counterable;
        ctx = c;
        try {
            t.invulnerableTime = 0;
            return t.hurt(atk.level().damageSources().playerAttack(atk), dmg);
        } finally {
            ctx = prev;
        }
    }

    public static void knock(LivingEntity t, Vec3 v) {
        t.setDeltaMovement(v);
        t.hurtMarked = true;
        t.fallDistance = 0;
    }

    public static void knock(LivingEntity t, double x, double y, double z) {
        knock(t, new Vec3(x, y, z));
    }

    /** Horizontal unit vector from a to b. */
    public static Vec3 away(Entity from, Entity to) {
        Vec3 d = to.position().subtract(from.position());
        Vec3 h = new Vec3(d.x, 0, d.z);
        if (h.lengthSqr() < 1.0E-4) return flat(from);
        return h.normalize();
    }

    public static Vec3 flat(Entity e) {
        Vec3 l = e.getLookAngle();
        Vec3 f = new Vec3(l.x, 0, l.z);
        return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    public static Vec3 mid(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() / 2.0, 0);
    }

    public static void place(LivingEntity t, Vec3 pos) {
        if (t instanceof ServerPlayer sp) {
            sp.teleportTo(pos.x, pos.y, pos.z);
        } else {
            t.setPos(pos.x, pos.y, pos.z);
        }
        knock(t, Vec3.ZERO);
    }

    public static boolean facing(LivingEntity victim, Vec3 from) {
        Vec3 look = victim.getLookAngle();
        Vec3 l = new Vec3(look.x, 0, look.z);
        Vec3 to = from.subtract(victim.position());
        Vec3 t = new Vec3(to.x, 0, to.z);
        if (l.lengthSqr() < 1.0E-4 || t.lengthSqr() < 1.0E-4) return true;
        return l.normalize().dot(t.normalize()) > 0.17;
    }

    /** First solid block hit by the look ray, or the max-range point. */
    public static Vec3 rayPoint(ServerPlayer p, double range) {
        Vec3 s = p.getEyePosition();
        Vec3 e = s.add(p.getLookAngle().scale(range));
        BlockHitResult r = p.level().clip(new ClipContext(s, e, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return r.getType() == HitResult.Type.MISS ? e : r.getLocation();
    }

    private record Hit(LivingEntity e, double t) {}

    /** Living entities near the look ray, nearest first. */
    public static List<LivingEntity> line(ServerPlayer p, double range, double radius) {
        ServerLevel level = lv(p);
        Vec3 s = p.getEyePosition();
        Vec3 d = p.getLookAngle();
        AABB box = p.getBoundingBox().expandTowards(d.scale(range)).inflate(radius + 1.0);
        List<Hit> hits = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isSpectator())) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(s);
            double t = to.dot(d);
            if (t < 0 || t > range + e.getBbWidth()) continue;
            double perp = to.subtract(d.scale(t)).length();
            if (perp <= radius + e.getBbWidth() / 2.0 + 0.2) {
                hits.add(new Hit(e, t));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::t));
        List<LivingEntity> out = new ArrayList<>();
        for (Hit h : hits) out.add(h.e());
        return out;
    }

    public static List<LivingEntity> around(ServerPlayer p, Vec3 c, double r) {
        return lv(p).getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                x -> x != p && x.isAlive() && !x.isSpectator() && x.position().distanceTo(c) <= r + x.getBbWidth());
    }

    public static void trail(Vec3 from, Vec3 to, double step, java.util.function.Consumer<Vec3> fn) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0E-4) return;
        Vec3 dir = delta.scale(1.0 / len);
        for (double d = 0; d <= len; d += step) {
            fn.accept(from.add(dir.scale(d)));
        }
    }
}
