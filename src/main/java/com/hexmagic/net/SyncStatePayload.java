package com.hexmagic.net;

import com.hexmagic.HexMagic;
import com.hexmagic.ability.HexAbility;
import com.hexmagic.ability.HexPlayerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> owning client: everything the HUD needs. The client counts the timers down locally. */
public record SyncStatePayload(int charges, int maxCharges, int chargeRegen, int chargeRegenMax, int[] cooldown, int[] cooldownMax,
                               int passivesDisabled, int selected, int idleTicks, int idleMax, boolean swarmReady, boolean onPlatform)
        implements CustomPacketPayload {
    public static final Type<SyncStatePayload> TYPE = new Type<>(HexMagic.id("sync_state"));
    public static final StreamCodec<FriendlyByteBuf, SyncStatePayload> CODEC = StreamCodec.of(SyncStatePayload::write, SyncStatePayload::read);

    public static SyncStatePayload of(HexPlayerData d, int maxCharges, int regenMax, int idleMax, boolean swarmReady) {
        return new SyncStatePayload(d.charges, maxCharges, d.chargeRegen, regenMax, d.cooldown.clone(), d.cooldownMax.clone(),
                d.passivesDisabled, d.selected, d.idleTicks, idleMax, swarmReady, d.onPlatform);
    }

    private static void write(FriendlyByteBuf buf, SyncStatePayload p) {
        buf.writeVarInt(p.charges);
        buf.writeVarInt(p.maxCharges);
        buf.writeVarInt(p.chargeRegen);
        buf.writeVarInt(p.chargeRegenMax);
        for (int i = 0; i < HexAbility.COUNT; i++) {
            buf.writeVarInt(p.cooldown[i]);
            buf.writeVarInt(p.cooldownMax[i]);
        }
        buf.writeVarInt(p.passivesDisabled);
        buf.writeVarInt(p.selected);
        buf.writeVarInt(p.idleTicks);
        buf.writeVarInt(p.idleMax);
        buf.writeBoolean(p.swarmReady);
        buf.writeBoolean(p.onPlatform);
    }

    private static SyncStatePayload read(FriendlyByteBuf buf) {
        int charges = buf.readVarInt();
        int maxCharges = buf.readVarInt();
        int regen = buf.readVarInt();
        int regenMax = buf.readVarInt();
        int[] cd = new int[HexAbility.COUNT];
        int[] cdMax = new int[HexAbility.COUNT];
        for (int i = 0; i < HexAbility.COUNT; i++) {
            cd[i] = buf.readVarInt();
            cdMax[i] = buf.readVarInt();
        }
        return new SyncStatePayload(charges, maxCharges, regen, regenMax, cd, cdMax,
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
