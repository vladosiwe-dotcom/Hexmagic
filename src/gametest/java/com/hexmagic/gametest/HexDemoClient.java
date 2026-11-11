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

    private static final boolean ENABLED = Boolean.getBoolean("hexmagic.demo");
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
        at(1, () -> {
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
        at(14, () -> shot("01_equip_anim"));
        at(30, () -> shot("02_staff_third_person"));
        at(34, () -> { camera(CameraType.FIRST_PERSON); look(0, 5); });
        at(36, () -> shot("03_staff_first_person"));
        at(40, () -> press(HexAbility.SHIELD));
        at(45, () -> shot("04_shield_forming"));
        at(62, () -> shot("05_shield"));
        at(70, () -> { camera(CameraType.THIRD_PERSON_BACK); lookAt("A"); press(HexAbility.DRILL); });
        at(84, () -> shot("06_drill_charging"));
        at(118, () -> shot("07_drill_charging_full"));
        at(132, () -> { camera(CameraType.FIRST_PERSON); shot("08_drill_first_person"); });
        at(134, () -> release(HexAbility.DRILL));
        at(136, () -> { camera(CameraType.THIRD_PERSON_BACK); });
        at(138, () -> shot("09_drill_flying"));
        at(143, () -> shot("10_drill_impact"));
        at(160, () -> { look(0, 0); press(HexAbility.SHIELD); });
        at(185, () -> { lookAt("B"); press(HexAbility.SPIKE); });
        at(193, () -> shot("11_spike_windup"));
        at(202, () -> shot("12_spike_flying"));
        at(225, () -> { cmd("tp @s 0 -60 -6"); });
        at(230, () -> { camera(CameraType.THIRD_PERSON_BACK); look(180, 10); press(HexAbility.LAUNCH); });
        at(235, () -> shot("13_launch_platform"));
        at(252, () -> shot("14_platform_rising"));
        at(290, () -> shot("15_platform_top"));
        at(295, () -> { camera(CameraType.THIRD_PERSON_BACK); cmd("tp @s 6 -58 0"); });
        at(330, () -> shot("16_landing"));
        at(350, () -> { cmd("tp @s 0 -60 0"); cmd("summon zombie -4 -60 9 {NoAI:1b,CustomName:'\"D\"',PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",count:1}]}"); });
        at(360, () -> { lookAt("D"); press(HexAbility.PRISON); });
        at(366, () -> shot("17_prison_prep"));
        at(385, () -> shot("18_prison"));
        at(530, () -> shot("19_prison_closing"));
        at(565, () -> shot("20_prison_crush"));
        at(640, () -> { camera(CameraType.THIRD_PERSON_FRONT); look(180, -10); });
        at(650, () -> shot("21_hex_attack_swarm"));
        at(645, () -> cmd("summon zombie 0 -60 9 {NoAI:1b,CustomName:'\"E\"',PersistenceRequired:1b,ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",count:1}]}"));
        at(655, () -> { camera(CameraType.THIRD_PERSON_BACK); lookAt("E"); press(HexAbility.HEX_ATTACK); });
        at(672, () -> shot("22_hex_attack_lock"));
        at(690, () -> shot("23_hex_attack_volley"));
        at(720, () -> { look(0, 0); cmd("summon arrow 0 -58.4 6 {Motion:[0.0,0.0,-1.2],pickup:0b}"); });
        at(725, () -> shot("24_reflect"));
        at(728, () -> shot("25_reflect_2"));
        at(760, () -> Minecraft.getInstance().stop());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (++waitTicks % 100 == 0) LOGGER.info("[HexDemo] waiting, screen={} overlay={}", mc.screen == null ? null : mc.screen.getClass().getSimpleName(), mc.getOverlay());
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
        t++;
        if (t % 50 == 0) LOGGER.info("[HexDemo] tick {}", t);
        for (Step s : STEPS) if (s.tick == t) s.action.run();
    }
}
