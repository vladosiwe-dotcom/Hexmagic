package com.hexmagic.net;

import com.hexmagic.HexMagic;
import com.hexmagic.ability.HexAbility;
import com.hexmagic.ability.HexCasting;
import com.hexmagic.client.HexClientHooks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = HexMagic.MOD_ID)
public final class HexNetwork {
    private HexNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AbilityInputPayload.TYPE, AbilityInputPayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                HexAbility ability = HexAbility.byIndex(payload.ability());
                if (ability == null) return;
                if (payload.pressed()) HexCasting.press(sp, ability);
                else HexCasting.release(sp, ability);
            }
        });
        registrar.playToServer(SelectAbilityPayload.TYPE, SelectAbilityPayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) HexCasting.select(sp, payload.index());
        });
        registrar.playToServer(AttackConstructPayload.TYPE, AttackConstructPayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) HexCasting.attackConstruct(sp, payload.entityId());
        });
        registrar.playToClient(SyncStatePayload.TYPE, SyncStatePayload.CODEC, (payload, ctx) -> HexClientHooks.onSyncState(payload));
        registrar.playToClient(HexFxPayload.TYPE, HexFxPayload.CODEC, (payload, ctx) -> HexClientHooks.onFx(payload));
    }
}
