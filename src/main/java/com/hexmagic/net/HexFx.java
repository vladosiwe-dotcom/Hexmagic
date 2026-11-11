package com.hexmagic.net;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Effect ids and the server-side helper that broadcasts them to nearby players. */
public final class HexFx {
    private HexFx() {}

    /** Burst of cells at {@code pos} facing {@code dir}. a = size, b = heat (0 blue .. 1 red). */
    public static final int IMPACT = 0;
    /** Small shield that flashes in front of a reflected projectile, then shatters. a = size. */
    public static final int REFLECT = 1;
    /** Landing cushion under a falling caster. a = radius, b = 0 forming / 1 absorbing the landing. */
    public static final int CUSHION = 2;
    /** Ring of displaced air. a = radius, b = strength. */
    public static final int GUST = 3;
    /** Two walls that swing shut in front of the caster (entityId) before a Hex Prison. */
    public static final int PRISON_PREP = 4;
    /** Ripple across a construct (entityId) from the world point {@code pos}. a = strength. */
    public static final int WALL_HIT = 5;
    /** Honeycomb reticle closing in on a target (entityId). a = duration in ticks. */
    public static final int TARGET_LOCK = 6;
    /** Shock ring left behind by a launching drill or platform. a = radius. */
    public static final int LAUNCH_RING = 7;
    /** Flash when the prison walls meet. a = size. */
    public static final int CRUSH = 8;

    public static void send(ServerLevel level, int kind, Vec3 pos, Vec3 dir, float a, float b, int entityId) {
        HexFxPayload payload = new HexFxPayload(kind, pos, dir, a, b, entityId);
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(pos) > 96.0 * 96.0) continue;
            if (sp.connection != null && sp.connection.hasChannel(payload)) PacketDistributor.sendToPlayer(sp, payload);
        }
    }

    public static void send(ServerLevel level, int kind, Vec3 pos, Vec3 dir, float a, float b) {
        send(level, kind, pos, dir, a, b, -1);
    }
}
