package com.hexmagic.gametest;

import com.hexmagic.ability.HexAbility;
import com.hexmagic.net.AbilityInputPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Scripted showcase used for automated screenshots: creates a flat world, hands the player the staff
 * and walks through every ability. Enabled with -Dhexmagic.demo=true; never active otherwise.
 */
@EventBusSubscriber(modid = "hexmagic_gametest", value = Dist.CLIENT)
public final class HexDemoClient {
    private HexDemoClient() {}

    private static final String MODE = System.getProperty("hexmagic.demo", "");
    private static final boolean ENABLED = !MODE.isEmpty() && !MODE.equals("false");
    private static final boolean POSE_ONLY = MODE.equals("pose");
    private static final boolean OBSERVE = MODE.equals("observe");
    private static final boolean RMB = MODE.equals("rmb");
    private static int seen = -1;
    private static boolean started;
    private static int waitTicks;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("HexDemo");
    private static int t = -1;
    private static final List<Step> STEPS = new ArrayList<>();

    private record Step(int tick, Runnable action) {}

    private static void at(int tick, Runnable r) {
        STEPS.add(new Step(tick, r));
    }

    private static void press(HexAbility a) {
        PacketDistributor.sendToServer(new AbilityInputPayload(a.ordinal(), true));
    }

    private static void release(HexAbility a) {
        PacketDistributor.sendToServer(new AbilityInputPayload(a.ordinal(), false));
    }

    private static void cmd(String s) {
        Minecraft.getInstance().player.connection.sendCommand(s);
    }

    private static void look(float yaw, float pitch) {
        var p = Minecraft.getInstance().player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
    }

    private static void lookAt(String name) {
        var mc = Minecraft.getInstance();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity && e != mc.player && e.getCustomName() != null && e.getCustomName().getString().equals(name)) {
                Vec3 d = e.getBoundingBox().getCenter().subtract(mc.player.getEyePosition());
                float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
                float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
                look(yaw, pitch);
                return;
            }
        }
    }

    private static void dump(String tag) {
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        LOGGER.info("[HexDemo] {} player={} cam={} look={}", tag, mc.player.position(), cam, mc.player.getLookAngle());
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof com.hexmagic.entity.HexConstructEntity c) {
                LOGGER.info("[HexDemo]   construct id={} kind={} state={} pos={} yaw={} halfW={} halfH={} hp={} owner={}", c.getId(), c.getKind(), c.getState(), c.position(), c.getPanelYaw(), c.getHalfW(), c.getHalfH(), c.getHp(), c.getOwnerId());
            } else if (e instanceof com.hexmagic.entity.HexPrisonEntity p) {
                LOGGER.info("[HexDemo]   prison id={} pos={}", p.getId(), p.position());
            } else if (e instanceof LivingEntity l && e != mc.player) {
                LOGGER.info("[HexDemo]   living {} pos={} hp={}", l.getName().getString(), l.position(), l.getHealth());
            }
        }
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        LOGGER.info("[HexDemo] screenshot {}", name);
        dump(name);
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static void camera(CameraType type) {
        Minecraft.getInstance().options.setCameraType(type);
    }

    static {
        if (POSE_ONLY) poseScript();
        else if (RMB) rmbScript();
        else if (!OBSERVE) fullScript();
    }

    /** Second player: stands aside, keeps looking at "Dev" and photographs what a bystander sees. */
    private static void observeTick(Minecraft mc) {
        Entity dev = null;
        for (var p : mc.level.players()) if (p != mc.player && p.getGameProfile().getName().equals("Dev")) dev = p;
        if (dev == null) return;
        seen++;
        Vec3 d = dev.getBoundingBox().getCenter().add(0, 0.8, 0).subtract(mc.player.getEyePosition());
        look((float) (Math.atan2(-d.x, d.z) * 180 / Math.PI), (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI));
        int[] shots = {8, 11, 14, 30, 50, 64, 100, 120, 140, 196, 204, 240, 256, 300, 372, 390, 535, 570, 660, 692, 730};
        for (int t : shots) if (t == seen) shot("obs_" + String.format("%03d", t));
        if (seen == 770) mc.stop();
    }

    private static void holdUse(boolean down) {
        Minecraft.getInstance().options.keyUse.setDown(down);
    }

    private static int constructs() {
        int n = 0;
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) if (e instanceof com.hexmagic.entity.HexConstructEntity) n++;
        return n;
    }

    /** Exercises the right mouse button path with real use-item packets. */
    private static void rmbScript() {
        at(1, () -> {
            cmd("gamemode survival");
            cmd("time set 1000");
            cmd("tp @s 0 -60 0 0 0");
            cmd("item replace entity @s hotbar.0 with hexmagic:hex_staff");
            Minecraft.getInstance().player.getInventory().selected = 0;
            cmd("summon zombie 0 -60 9 {NoAI:1b,CustomName:'\"A\"',PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",count:1}]}");
        });
        at(20, () -> { camera(CameraType.THIRD_PERSON_BACK); look(0, 0); com.hexmagic.client.HexClientState.select(0); });
        // A held button: vanilla itself repeats the click every 4 ticks while the key is down.
        at(30, () -> holdUse(true));
        at(54, () -> holdUse(false));
        at(60, () -> LOGGER.info("[HexDemo] RESULT held-click shields={} charges={} (expect 1 and 2)", constructs(), com.hexmagic.client.HexClientState.charges));
        // A second, separate click.
        at(70, () -> holdUse(true));
        at(72, () -> holdUse(false));
        at(90, () -> LOGGER.info("[HexDemo] RESULT second click shields={} charges={} (expect 2 and 1)", constructs(), com.hexmagic.client.HexClientState.charges));
        at(92, () -> shot("rmb_01_shields"));
        // Drill: press and hold, then let go.
        at(100, () -> { lookAt("A"); com.hexmagic.client.HexClientState.select(1); });
        at(104, () -> holdUse(true));
        at(120, () -> LOGGER.info("[HexDemo] RESULT drill charging id={} usingItem={} (expect a drill and true)", com.hexmagic.client.HexClientState.chargingDrill, Minecraft.getInstance().player.isUsingItem()));
        at(150, () -> shot("rmb_02_drill_hold"));
        at(152, () -> holdUse(false));
        at(156, () -> shot("rmb_03_drill_released"));
        at(175, () -> {
            boolean dead = true;
            for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) if (e instanceof LivingEntity l && e.getCustomName() != null && l.isAlive()) dead = false;
            LOGGER.info("[HexDemo] RESULT drill cooldown={} targetDead={} usingItem={} (expect >0, true/hit, false)", com.hexmagic.client.HexClientState.cooldown[1], dead, Minecraft.getInstance().player.isUsingItem());
            dump("rmb_end");
        });
        at(185, () -> Minecraft.getInstance().stop());
    }

    private static void poseScript() {
        at(1, () -> {
            cmd("time set 1000");
            cmd("weather clear");
            cmd("tp @s 0 -60 0 0 0");
            cmd("item replace entity @s hotbar.0 with hexmagic:hex_staff");
            Minecraft.getInstance().player.getInventory().selected = 0;
        });
        // Alternative hand poses to compare against the model's own display transforms.
        float[][] v3 = {{-7, 7, 2, 90, 0, 0, 1.15f}, {-10, 7, 2, 90, 0, 0, 1.2f}, {1, 7, 2, 90, 0, 0, 1.2f}, {0, 4, 8, 0, 0, 0, 1.5f}};
        float[][] v1 = {{10, 0, -8, 0, 0, -20, 0.55f}, {8, -4, 0, 0, 0, 0, 0.45f}, {12, -8, -4, 0, 0, -20, 0.5f}, {4, 0, -10, 0, 0, 0, 0.6f}};
        int t = 25;
        for (int i = 0; i < v3.length; i++) {
            final float[] r = v3[i]; final int k = i;
            at(t, () -> { com.hexmagic.client.render.HexStaffRenderer.debugThirdPerson = r; camera(CameraType.THIRD_PERSON_FRONT); look(0, 0); });
            at(t + 2, () -> shot("rot_tp_front_" + k));
            at(t + 3, () -> camera(CameraType.THIRD_PERSON_BACK));
            at(t + 5, () -> shot("rot_tp_back_" + k));
            t += 6;
        }
        for (int i = 0; i < v1.length; i++) {
            final float[] r = v1[i]; final int k = i;
            at(t, () -> { com.hexmagic.client.render.HexStaffRenderer.debugFirstPerson = r; camera(CameraType.FIRST_PERSON); look(0, 0); });
            at(t + 2, () -> shot("rot_fp_" + k));
            t += 3;
        }
        at(t + 2, () -> Minecraft.getInstance().stop());
    }

    private static void fullScript() {
        at(1, () -> {
            cmd("gamemode creative");
            if (Boolean.getBoolean("hexmagic.demo.mp")) {
                cmd("gamemode creative Obs");
                cmd("tp Obs 6.5 -60 -5.5");
            }
            cmd("time set 1000");
            cmd("weather clear");
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule doMobSpawning false");
            cmd("tp @s 0 -60 0 0 0");
            cmd("item replace entity @s hotbar.0 with hexmagic:hex_staff");
            Minecraft.getInstance().player.getInventory().selected = 0;
            for (String z : new String[] {"0 -60 8 A", "3 -60 10 B", "-4 -60 9 C"}) {
                String[] p = z.split(" ");
                cmd("summon zombie " + p[0] + " " + p[1] + " " + p[2] + " {NoAI:1b,CustomName:'\"" + p[3] + "\"',PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",count:1}]}");
            }
        });
        at(10, () -> { camera(CameraType.THIRD_PERSON_FRONT); look(0, 0); });
        float[][] tp = {
            {0, 4, 0.5f, 0, 0, 180, 0.9f},
            {0, 4, 1.25f, 0, 0, 0, 1f},
            {0, 2, 0.5f, -80, 0, 0, 0.8f},
            {0, 3, 0, 180, 0, 0, 0.8f},
            {-1, 2, 0, 160, 0, -20, 0.8f},
            {0, 3, 1, 0, 90, 180, 0.8f},
        };
        float[][] fp = {
            {1.5f, 0, -2.75f, 0, 0, 0, 1f},
            {3, -1, -2.75f, 0, 0, 0, 0.6f},
            {3, -2, -3, 10, -20, 0, 0.6f},
            {2, 1, -1.5f, 0, -45, 20, 0.5f},
            {4, -3, -4, -10, 0, 0, 0.7f},
            {2.5f, -1.5f, -2.5f, 0, -30, -10, 0.55f},
        };
        for (int i = 0; i < tp.length; i++) {
            final float[] v = tp[i]; final int k = i;
            at(760 + i * 4, () -> { com.hexmagic.client.render.HexStaffRenderer.debugThirdPerson = v; camera(CameraType.THIRD_PERSON_FRONT); look(0, 0); });
            at(762 + i * 4, () -> shot("pose_tp_front_" + k));
        }
        for (int i = 0; i < tp.length; i++) {
            final float[] v = tp[i]; final int k = i;
            at(790 + i * 4, () -> { com.hexmagic.client.render.HexStaffRenderer.debugThirdPerson = v; camera(CameraType.THIRD_PERSON_BACK); look(0, 0); });
            at(792 + i * 4, () -> shot("pose_tp_back_" + k));
        }
        for (int i = 0; i < fp.length; i++) {
            final float[] v = fp[i]; final int k = i;
            at(820 + i * 4, () -> { com.hexmagic.client.render.HexStaffRenderer.debugFirstPerson = v; camera(CameraType.FIRST_PERSON); look(0, 0); });
            at(822 + i * 4, () -> shot("pose_fp_" + k));
        }
        at(6, () -> shot("00_equip_anim_a"));
        at(10, () -> shot("01_equip_anim"));
        at(13, () -> shot("01b_equip_anim_c"));
        at(30, () -> shot("02_staff_third_person"));
        at(34, () -> { camera(CameraType.FIRST_PERSON); look(0, 5); });
        at(36, () -> shot("03_staff_first_person"));
        at(40, () -> press(HexAbility.SHIELD));
        at(45, () -> shot("04_shield_forming"));
        at(62, () -> shot("05_shield"));
        at(69, () -> { camera(CameraType.THIRD_PERSON_BACK); lookAt("A"); });
        at(70, () -> press(HexAbility.DRILL));
        at(84, () -> shot("06_drill_charging"));
        at(118, () -> shot("07_drill_charging_full"));
        at(132, () -> { camera(CameraType.FIRST_PERSON); shot("08_drill_first_person"); });
        at(134, () -> release(HexAbility.DRILL));
        at(136, () -> { camera(CameraType.THIRD_PERSON_BACK); });
        at(138, () -> shot("09_drill_flying"));
        at(143, () -> shot("10_drill_impact"));
        at(160, () -> { look(0, 0); press(HexAbility.SHIELD); });
        at(184, () -> lookAt("B"));
        at(185, () -> press(HexAbility.SPIKE));
        at(193, () -> shot("11_spike_windup"));
        at(202, () -> shot("12_spike_flying"));
        at(225, () -> { cmd("tp @s 0 -60 -6"); });
        at(230, () -> { camera(CameraType.THIRD_PERSON_BACK); look(180, 10); press(HexAbility.LAUNCH); });
        at(235, () -> shot("13_launch_platform"));
        at(252, () -> shot("14_platform_rising"));
        at(290, () -> shot("15_platform_top"));
        at(295, () -> { camera(CameraType.THIRD_PERSON_BACK); look(0, 25); cmd("tp @s 6 -46 0"); });
        at(318, () -> shot("16_falling"));
        at(322, () -> shot("16b_cushion"));
        at(325, () -> shot("16c_landing"));
        at(329, () -> shot("16d_landed"));
        at(350, () -> { cmd("tp @s 0 -60 0"); cmd("summon zombie -4 -60 9 {NoAI:1b,CustomName:'\"D\"',PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",count:1}]}"); });
        at(358, () -> lookAt("D"));
        at(360, () -> press(HexAbility.PRISON));
        at(366, () -> shot("17_prison_prep"));
        at(385, () -> shot("18_prison"));
        at(530, () -> shot("19_prison_closing"));
        at(565, () -> shot("20_prison_crush"));
        at(640, () -> { camera(CameraType.THIRD_PERSON_FRONT); look(180, -10); });
        at(650, () -> shot("21_hex_attack_swarm"));
        at(645, () -> cmd("summon zombie 0 -60 9 {NoAI:1b,CustomName:'\"E\"',PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",count:1}]}"));
        at(653, () -> { camera(CameraType.THIRD_PERSON_BACK); lookAt("E"); });
        at(655, () -> press(HexAbility.HEX_ATTACK));
        at(672, () -> shot("22_hex_attack_lock"));
        at(690, () -> shot("23_hex_attack_volley"));
        at(718, () -> { cmd("tp @s 12 -60 0"); look(0, 0); });
        at(720, () -> cmd("summon arrow 12 -58.4 6 {Motion:[0.0,0.0,-1.2],pickup:0b}"));
        at(726, () -> shot("24_reflect"));
        at(728, () -> shot("25_reflect_2"));
        at(731, () -> shot("26_reflect_3"));
        at(850, () -> Minecraft.getInstance().stop());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (++waitTicks % 100 == 0) LOGGER.info("[HexDemo] waiting, screen={} overlay={}", mc.screen == null ? null : mc.screen.getClass().getSimpleName(), mc.getOverlay());
            if (Boolean.getBoolean("hexmagic.demo.mp")) return; // quick play connects by itself
            if (!started && mc.screen != null && !(mc.screen instanceof TitleScreen) && waitTicks > 20) {
                // First launch shows the accessibility onboarding; skip straight to the title screen.
                mc.options.onboardAccessibility = false;
                mc.options.save();
                mc.setScreen(new TitleScreen(false));
            }
            if (mc.screen instanceof TitleScreen && !started) {
                LOGGER.info("[HexDemo] creating world");
                started = true;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                LevelSettings settings = new LevelSettings("HexDemo", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("HexDemo", settings, new WorldOptions(1234L, false, false),
                        ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            return;
        }
        if (mc.player == null || mc.screen != null) return;
        if (OBSERVE) {
            observeTick(mc);
            return;
        }
        t++;
        if (t % 50 == 0) LOGGER.info("[HexDemo] tick {}", t);
        for (Step s : STEPS) if (s.tick == t) s.action.run();
    }
}
