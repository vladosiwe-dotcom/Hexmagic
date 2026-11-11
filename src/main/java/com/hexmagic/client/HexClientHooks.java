package com.hexmagic.client;

import com.hexmagic.net.HexFxPayload;
import com.hexmagic.net.SyncStatePayload;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

/**
 * Entry points the common code may call that end up in client-only classes. On a dedicated server
 * these payloads are never received, so the client classes behind them are never loaded.
 */
public final class HexClientHooks {
    private HexClientHooks() {}

    public static void onSyncState(SyncStatePayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) HexClientState.accept(payload);
    }

    public static void onFx(HexFxPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) com.hexmagic.client.fx.HexFxManager.spawn(payload);
    }
}
