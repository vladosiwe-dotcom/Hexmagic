package com.hexmagic.client;

import com.hexmagic.HexConfig;
import com.hexmagic.HexMagic;
import com.hexmagic.ability.HexAbility;
import com.hexmagic.ability.HexTargeting;
import com.hexmagic.client.fx.HexFxManager;
import com.hexmagic.entity.HexConstructEntity;
import com.hexmagic.entity.HexDrillEntity;
import com.hexmagic.net.AbilityInputPayload;
import com.hexmagic.net.AttackConstructPayload;
import com.hexmagic.registry.HexItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(modid = HexMagic.MOD_ID, value = Dist.CLIENT)
public final class HexClientEvents {
    private HexClientEvents() {}

    private static boolean drillKeyDown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;
        if (!mc.isPaused()) {
            HexClientState.tick();
            HexFxManager.tick();
            HexEquipAnimations.tick(level);
            HexClientState.lastSelectChange++;
        }
        boolean holding = HexItems.isHoldingStaff(player) && !player.isSpectator();
        handleKeys(mc, player, holding);
        if (holding) {
            trackChargingDrill(level, player);
            predictWalls(level, player);
        } else {
            HexClientState.chargingDrill = -1;
            if (drillKeyDown) {
                drillKeyDown = false;
                PacketDistributor.sendToServer(new AbilityInputPayload(HexAbility.DRILL.ordinal(), false));
            }
        }
    }

    private static void handleKeys(Minecraft mc, LocalPlayer player, boolean holding) {
        boolean screenOpen = mc.screen != null;
        for (int i = 0; i < HexAbility.COUNT; i++) {
            KeyMapping key = HexKeys.ABILITY_KEYS[i];
            if (key == null) continue;
            if (i == HexAbility.DRILL.ordinal()) {
                boolean down = key.isDown() && holding && !screenOpen;
                if (down && !drillKeyDown) PacketDistributor.sendToServer(new AbilityInputPayload(i, true));
                if (!down && drillKeyDown) PacketDistributor.sendToServer(new AbilityInputPayload(i, false));
                drillKeyDown = down;
                while (key.consumeClick()) {}
                continue;
            }
            boolean pressed = false;
            while (key.consumeClick()) pressed = true;
            if (pressed && holding && !screenOpen) PacketDistributor.sendToServer(new AbilityInputPayload(i, true));
        }
        boolean next = false, prev = false;
        while (HexKeys.NEXT.consumeClick()) next = true;
        while (HexKeys.PREV.consumeClick()) prev = true;
        if (holding && !screenOpen) {
            if (next) HexClientState.cycle(1);
            if (prev) HexClientState.cycle(-1);
        }
    }

    private static void trackChargingDrill(ClientLevel level, LocalPlayer player) {
        if (HexClientState.chargingDrill != -1 && level.getEntity(HexClientState.chargingDrill) instanceof HexDrillEntity d
                && d.getState() == HexDrillEntity.ST_CHARGING) {
            return;
        }
        HexClientState.chargingDrill = -1;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof HexDrillEntity d && d.getState() == HexDrillEntity.ST_CHARGING && d.getOwner() == player) {
                HexClientState.chargingDrill = d.getId();
                return;
            }
        }
    }

    /** The local player is moved by their own client; keep them out of enemy barriers right away. */
    private static void predictWalls(ClientLevel level, LocalPlayer player) {
        boolean ownerPasses = HexConfig.OWNER_PASSES_THROUGH.get();
        for (HexConstructEntity wall : level.getEntitiesOfClass(HexConstructEntity.class, player.getBoundingBox().inflate(1.5))) {
            if (!wall.isWall() || wall.getState() != HexConstructEntity.ST_ACTIVE) continue;
            if (ownerPasses && isFriendlyClient(level, wall, player)) continue;
            Vec3 push = wall.computePush(player);
            if (push == null) continue;
            player.setPos(player.getX() + push.x, player.getY(), player.getZ() + push.z);
            Vec3 vel = player.getDeltaMovement();
            double len = push.length();
            if (len > 1.0e-6) {
                double nx = push.x / len, nz = push.z / len;
                double into = vel.x * nx + vel.z * nz;
                if (into < 0) player.setDeltaMovement(vel.x - nx * into, vel.y, vel.z - nz * into);
            }
        }
    }

    private static boolean isFriendlyClient(ClientLevel level, HexConstructEntity wall, LocalPlayer player) {
        if (wall.getOwnerId() == player.getId()) return true;
        Entity owner = level.getEntity(wall.getOwnerId());
        return owner != null && HexTargeting.areAllied(owner, player);
    }

    /** Left click on a barrier: nothing is pickable there, so the client finds the panel itself. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || player.isSpectator()) return;
        double reach = player.entityInteractionRange() + 1.0;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        double limit = 1.0;
        HitResult hr = mc.hitResult;
        if (hr != null && hr.getType() != HitResult.Type.MISS) limit = Math.min(1.0, hr.getLocation().distanceTo(eye) / reach);
        HexConstructEntity best = null;
        double bestT = limit;
        for (HexConstructEntity wall : level.getEntitiesOfClass(HexConstructEntity.class, player.getBoundingBox().inflate(reach + 4))) {
            if (!wall.isSolid() || isFriendlyClient(level, wall, player)) continue;
            Vector3f hit = wall.intersect(eye, end, 0.25);
            if (hit != null && hit.z < bestT) {
                bestT = hit.z;
                best = wall;
            }
        }
        if (best == null) return;
        PacketDistributor.sendToServer(new AttackConstructPayload(best.getId()));
        player.swing(InteractionHand.MAIN_HAND);
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        HexFxManager.render(event.getPoseStack(), mc.renderBuffers().bufferSource(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float scale = HexClientConfig.SCREEN_SHAKE.get().floatValue();
        if (scale <= 0f) return;
        float shake = HexFxManager.shake((float) event.getPartialTick()) * scale;
        if (shake <= 0.0005f) return;
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level == null ? 0 : mc.level.getGameTime() % 10000) + (float) event.getPartialTick();
        // Three out-of-phase sines read as a rattle rather than a swing.
        event.setYaw(event.getYaw() + shake * 1.6f * Mth.sin(time * 2.9f));
        event.setPitch(event.getPitch() + shake * 1.6f * Mth.sin(time * 3.7f + 1.3f));
        event.setRoll(event.getRoll() + shake * 1.1f * Mth.sin(time * 4.3f + 2.1f));
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        HexEquipAnimations.renderingEntity = event.getEntity();
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        HexEquipAnimations.renderingEntity = null;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        HexEquipAnimations.renderingEntity = Minecraft.getInstance().player;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HexFxManager.clear();
        HexEquipAnimations.clear();
        HexClientState.reset();
        drillKeyDown = false;
    }
}
