package com.hexmagic.net;

import com.hexmagic.HexMagic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server -> client: a purely visual honeycomb effect. Gameplay never depends on these. */
public record HexFxPayload(int kind, Vec3 pos, Vec3 dir, float a, float b, int entityId) implements CustomPacketPayload {
    public static final Type<HexFxPayload> TYPE = new Type<>(HexMagic.id("fx"));
    public static final StreamCodec<FriendlyByteBuf, HexFxPayload> CODEC = StreamCodec.of(HexFxPayload::write, HexFxPayload::read);

    private static void write(FriendlyByteBuf buf, HexFxPayload p) {
        buf.writeByte(p.kind);
        buf.writeDouble(p.pos.x);
        buf.writeDouble(p.pos.y);
        buf.writeDouble(p.pos.z);
        buf.writeFloat((float) p.dir.x);
        buf.writeFloat((float) p.dir.y);
        buf.writeFloat((float) p.dir.z);
        buf.writeFloat(p.a);
        buf.writeFloat(p.b);
        buf.writeVarInt(p.entityId);
    }

    private static HexFxPayload read(FriendlyByteBuf buf) {
        int kind = buf.readByte();
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 dir = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new HexFxPayload(kind, pos, dir, buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
