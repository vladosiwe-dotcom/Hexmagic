package com.hexmagic.net;

import com.hexmagic.HexMagic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: an ability key went down or up. */
public record AbilityInputPayload(int ability, boolean pressed) implements CustomPacketPayload {
    public static final Type<AbilityInputPayload> TYPE = new Type<>(HexMagic.id("ability_input"));
    public static final StreamCodec<ByteBuf, AbilityInputPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityInputPayload::ability,
            ByteBufCodecs.BOOL, AbilityInputPayload::pressed,
            AbilityInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
