package com.example.jjsmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: everything the HUD needs. Empty cls = no moveset. */
public record HudPayload(String cls, boolean awakened, float hp, float maxHp, float guard, float awake,
                         boolean blocking, boolean guardBroken, boolean bfCue, int tojiWeapon,
                         int[] left, int[] total) implements CustomPacketPayload {
    public static final Type<HudPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JJSMod.MODID, "hud"));

    public static final StreamCodec<FriendlyByteBuf, HudPayload> CODEC = StreamCodec.of(HudPayload::write, HudPayload::read);

    public static HudPayload empty() {
        return new HudPayload("", false, 0, 0, 0, 0, false, false, false, 0, new int[6], new int[6]);
    }

    private static void write(FriendlyByteBuf buf, HudPayload p) {
        buf.writeUtf(p.cls);
        buf.writeBoolean(p.awakened);
        buf.writeFloat(p.hp);
        buf.writeFloat(p.maxHp);
        buf.writeFloat(p.guard);
        buf.writeFloat(p.awake);
        buf.writeBoolean(p.blocking);
        buf.writeBoolean(p.guardBroken);
        buf.writeBoolean(p.bfCue);
        buf.writeVarInt(p.tojiWeapon);
        for (int i = 0; i < 6; i++) {
            buf.writeVarInt(p.left[i]);
            buf.writeVarInt(p.total[i]);
        }
    }

    private static HudPayload read(FriendlyByteBuf buf) {
        String cls = buf.readUtf();
        boolean awakened = buf.readBoolean();
        float hp = buf.readFloat();
        float maxHp = buf.readFloat();
        float guard = buf.readFloat();
        float awake = buf.readFloat();
        boolean blocking = buf.readBoolean();
        boolean broken = buf.readBoolean();
        boolean cue = buf.readBoolean();
        int weapon = buf.readVarInt();
        int[] left = new int[6];
        int[] total = new int[6];
        for (int i = 0; i < 6; i++) {
            left[i] = buf.readVarInt();
            total[i] = buf.readVarInt();
        }
        return new HudPayload(cls, awakened, hp, maxHp, guard, awake, blocking, broken, cue, weapon, left, total);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
