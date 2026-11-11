package com.hexmagic.net;

import com.hexmagic.HexMagic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player swung at a honeycomb construct under the crosshair. */
public record AttackConstructPayload(int entityId) implements CustomPacketPayload {
    public static final Type<AttackConstructPayload> TYPE = new Type<>(HexMagic.id("attack_construct"));
    public static final StreamCodec<ByteBuf, AttackConstructPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(AttackConstructPayload::new, AttackConstructPayload::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
