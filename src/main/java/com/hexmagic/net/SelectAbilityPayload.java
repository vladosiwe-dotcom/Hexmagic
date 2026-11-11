package com.hexmagic.net;

import com.hexmagic.HexMagic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: which ability the "use" button should cast. */
public record SelectAbilityPayload(int index) implements CustomPacketPayload {
    public static final Type<SelectAbilityPayload> TYPE = new Type<>(HexMagic.id("select_ability"));
    public static final StreamCodec<ByteBuf, SelectAbilityPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(SelectAbilityPayload::new, SelectAbilityPayload::index);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
