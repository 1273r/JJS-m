package com.example.jjsmod;

import net.minecraft.server.level.ServerPlayer;

/** One in-progress move. Combat Instincts / stuns can cancel it. */
public class Cast {
    public final String id;
    public final boolean interruptible;
    public final long start;
    public final long windupEnd;
    public boolean cancelled;
    // Divergent Fist / Black Flash timing state
    public boolean bf;
    public boolean bfLocked;
    public long bfAt;

    public Cast(String id, boolean interruptible, long start, long windupEnd) {
        this.id = id;
        this.interruptible = interruptible;
        this.start = start;
        this.windupEnd = windupEnd;
    }

    public boolean live(ServerPlayer p) {
        return !cancelled && p.isAlive() && !p.hasDisconnected();
    }
}
