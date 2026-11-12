package com.example.jjsmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: an input action. */
public record ActionPayload(int action, float a, float b) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JJSMod.MODID, "action"));

    public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.action);
                buf.writeFloat(p.a);
                buf.writeFloat(p.b);
            },
            buf -> new ActionPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
