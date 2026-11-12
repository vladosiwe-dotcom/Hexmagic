package com.hexmagic.gametest.film;

import com.hexmagic.ability.HexAbility;
import com.hexmagic.client.HexClientState;
import com.hexmagic.client.HexKeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client half of the showcase film (-Dhexmagic.demo=film): plays its role by player name. */
@EventBusSubscriber(modid = "hexmagic_gametest", value = Dist.CLIENT)
public final class HexFilmClient {
    private HexFilmClient() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("HexFilm");
    public static final boolean ACTIVE = "film".equals(System.getProperty("hexmagic.demo"));
    private static Marker camera;
    private static boolean loggedStart, loggedEnd;
    private static int lastTick = Integer.MIN_VALUE;

    private static String role(Minecraft mc) {
        return mc.player == null ? "" : mc.player.getGameProfile().getName();
    }

    private static boolean isCam(Minecraft mc) {
        return HexFilm.CAM.equals(role(mc));
    }

    private static double filmTime(Minecraft mc, float partial) {
        if (mc.level == null || HexFilm.clientT0 == Long.MIN_VALUE) return Double.NaN;
        return mc.level.getGameTime() - HexFilm.clientT0 + partial;
    }

    // ------------------------------------------------------------------ input of the two actors

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre event) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        double ft = filmTime(mc, 0f);
        if (Double.isNaN(ft) || mc.player == null) return;
        int t = (int) ft;
        if (t >= HexFilm.END + 25) {
            mc.options.keyUse.setDown(false);
            mc.options.keyUp.setDown(false);
            mc.stop();
            return;
        }
        String role = role(mc);
        boolean newTick = t != lastTick;
        lastTick = t;
        if (HexFilm.MAGE.equals(role)) {
            if (newTick) {
                if (t == HexFilm.MAGE_SELECT_DRILL) HexClientState.select(HexAbility.DRILL.ordinal());
                for (int[] c : HexFilm.MAGE_CLICKS) {
                    if (c[0] == t) KeyMapping.click(HexKeys.ABILITY_KEYS[c[1]].getKey());
                }
            }
            if (t >= 0) mc.options.keyUse.setDown(HexFilm.inside(HexFilm.MAGE_USE, t));
        } else if (HexFilm.RIVAL.equals(role)) {
            if (newTick) {
                for (int a : HexFilm.RIVAL_ATTACK) if (a == t) KeyMapping.click(mc.options.keyAttack.getKey());
            }
            if (t >= 0) {
                mc.options.keyUse.setDown(HexFilm.inside(HexFilm.RIVAL_USE, t));
                mc.options.keyUp.setDown(HexFilm.inside(HexFilm.RIVAL_FORWARD, t));
            }
        }
    }

    // ------------------------------------------------------------------ camera operator

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        if (!isCam(mc) || mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double ft = filmTime(mc, partial);
        if (Double.isNaN(ft)) ft = -40;
        if (ft >= 0 && !loggedStart) {
            loggedStart = true;
            LOGGER.info("[HexFilm] FILMWALL start {} ft {}", System.currentTimeMillis(), ft);
        }
        if (ft >= HexFilm.END && !loggedEnd) {
            loggedEnd = true;
            LOGGER.info("[HexFilm] FILMWALL end {} ft {}", System.currentTimeMillis(), ft);
        }
        ClientLevel level = mc.level;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
        HexFilm.Shot shot = HexFilm.shotAt(ft);
        if (shot.eyesOf() != null) {
            Player p = find(level, shot.eyesOf());
            if (p != null) {
                if (mc.getCameraEntity() != p) mc.setCameraEntity(p);
                return;
            }
        }
        if (camera == null || camera.level() != level) camera = EntityType.MARKER.create(level);
        double k = Mth.clamp((ft - shot.t0()) / Math.max(1.0, shot.t1() - shot.t0()), 0.0, 1.0);
        k = k * k * (3 - 2 * k);
        Vec3 pos = shot.from().lerp(shot.to(), k);
        Vec3 look = shot.look();
        if (shot.lookAt() != null) {
            Player p = find(level, shot.lookAt());
            look = p != null ? p.getPosition(partial).add(shot.look()) : pos.add(0, 0, 1);
        }
        Vec3 d = look.subtract(pos);
        float yaw = (float) (Math.atan2(-d.x, d.z) * 180.0 / Math.PI);
        float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI);
        camera.setPos(pos.x, pos.y, pos.z);
        camera.xo = camera.xOld = pos.x;
        camera.yo = camera.yOld = pos.y;
        camera.zo = camera.zOld = pos.z;
        camera.setYRot(yaw);
        camera.yRotO = yaw;
        camera.setXRot(pitch);
        camera.xRotO = pitch;
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
    }

    private static Player find(ClientLevel level, String name) {
        for (Player p : level.players()) if (p.getGameProfile().getName().equals(name)) return p;
        return null;
    }

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (ACTIVE && isCam(Minecraft.getInstance())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onToast(ToastAddEvent event) {
        if (ACTIVE) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (ACTIVE && isCam(mc) && event.getEntity() == mc.player) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ captions

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        if (!isCam(mc)) return;
        double ft = filmTime(mc, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (Double.isNaN(ft)) ft = -40;
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        for (HexFilm.Caption c : HexFilm.CAPTIONS) {
            if (ft < c.t0() || ft >= c.t1()) continue;
            float a = (float) Mth.clamp(Math.min(ft - c.t0(), c.t1() - ft) / 4.0, 0.0, 1.0);
            if (a < 0.04f) continue;
            float scale = c.title() ? 2.6f : 1.35f;
            float y = c.title() ? h * 0.08f : h - 34f;
            int tw = font.width(c.text());
            g.pose().pushPose();
            g.pose().translate(w / 2f, y, 0);
            g.pose().scale(scale, scale, 1f);
            int bg = (int) (a * (c.title() ? 90 : 150)) << 24;
            g.fill(-tw / 2 - 5, -4, tw / 2 + 5, 12, bg);
            int color = (c.title() ? 0x9EE6FF : 0xFFFFFF) | ((int) (a * 255) << 24);
            g.drawString(font, c.text(), -tw / 2, 0, color, true);
            g.pose().popPose();
        }
    }

    static InputConstants.Key unused() {
        return null;
    }
}
