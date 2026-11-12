package com.example.jjsmod;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class Fx {
    public static void dust(ServerLevel l, Vec3 p, float r, float g, float b, float size, int count, double spread) {
        l.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), size),
                p.x, p.y, p.z, count, spread, spread, spread, 0.0);
    }

    public static void sfx(ServerLevel l, Vec3 p, SoundEvent s, float vol, float pitch) {
        l.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sfx(ServerLevel l, Vec3 p, Holder<SoundEvent> s, float vol, float pitch) {
        sfx(l, p, s.value(), vol, pitch);
    }
}
