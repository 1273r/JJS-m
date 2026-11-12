package com.example.jjsmod;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = JJSMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Net {
    public static final int M1 = 0, DASH = 1, BLOCK_ON = 2, BLOCK_OFF = 3, SKILL = 4, AWAKEN = 5;

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, Net::onAction);
        r.playToClient(HudPayload.TYPE, HudPayload.CODEC, Net::onHud);
    }

    private static void onHud(HudPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.apply(p));
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private static void onAction(ActionPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp) || !sp.isAlive()) return;
            JState st = JState.peek(sp);
            if (st == null) return;
            switch (p.action()) {
                case M1 -> Moves.m1(sp, st);
                case DASH -> Moves.dash(sp, st, clamp(p.a()), clamp(p.b()));
                case BLOCK_ON -> Moves.setBlock(sp, st, true);
                case BLOCK_OFF -> Moves.setBlock(sp, st, false);
                case SKILL -> Moves.skill(sp, st, Math.round(p.a()));
                case AWAKEN -> Moves.awaken(sp, st);
                default -> { }
            }
        });
    }
}
