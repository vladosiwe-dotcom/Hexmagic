package com.hexmagic.client;

import com.hexmagic.registry.HexItems;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Tracks when any player draws the Hex Staff so every client can play the "out of the void" summon
 * animation for it. Each client watches hand contents itself, so nothing needs to be sent.
 */
public final class HexEquipAnimations {
    private HexEquipAnimations() {}

    public static final int DURATION = 18;

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();
    /** Entity whose held items are being drawn right now, so the item renderer knows whom to animate. */
    @Nullable
    public static LivingEntity renderingEntity;

    public static void tick(ClientLevel level) {
        long now = level.getGameTime();
        for (Player p : level.players()) {
            State s = STATES.computeIfAbsent(p.getId(), k -> new State());
            boolean main = HexItems.isStaff(p.getMainHandItem());
            boolean off = HexItems.isStaff(p.getOffhandItem());
            if (main && !s.main) start(level, p, s, InteractionHand.MAIN_HAND, now);
            if (off && !s.off) start(level, p, s, InteractionHand.OFF_HAND, now);
            s.main = main;
            s.off = off;
            s.seen = now;
        }
        STATES.values().removeIf(s -> now - s.seen > 40);
    }

    private static void start(ClientLevel level, Player p, State s, InteractionHand hand, long now) {
        if (hand == InteractionHand.MAIN_HAND) s.mainStart = now;
        else s.offStart = now;
        level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), com.hexmagic.registry.HexSounds.SUMMON.get(), SoundSource.PLAYERS, 0.8f, 1.0f, false);
    }

    /** Animation progress 0..1 for the entity's hand, or 1 when no animation is running. */
    public static float progress(@Nullable LivingEntity entity, InteractionHand hand, float partial) {
        if (entity == null) return 1f;
        State s = STATES.get(entity.getId());
        if (s == null) return 1f;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 1f;
        long start = hand == InteractionHand.MAIN_HAND ? s.mainStart : s.offStart;
        if (start < 0) return 1f;
        float t = (level.getGameTime() - start + partial) / DURATION;
        return t >= 1f ? 1f : Math.max(0f, t);
    }

    public static void clear() {
        STATES.clear();
    }

    private static final class State {
        boolean main, off;
        long mainStart = -1, offStart = -1;
        long seen;
    }
}
