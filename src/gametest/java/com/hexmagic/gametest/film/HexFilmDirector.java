package com.hexmagic.gametest.film;

import com.hexmagic.HexConfig;
import com.hexmagic.ability.HexAbility;
import com.hexmagic.ability.HexCasting;
import com.hexmagic.ability.HexPlayerData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server half of the showcase film (only with -Dhexmagic.director=true): stages the scene, places
 * the players and aims them. Every ability itself is cast by the caster's client pressing keys.
 */
@EventBusSubscriber(modid = "hexmagic_gametest")
public final class HexFilmDirector {
    private HexFilmDirector() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("HexFilm");
    private static final boolean ENABLED = Boolean.getBoolean("hexmagic.director");
    private static long t0 = -1;
    private static int ready;
    private static boolean done;

    private static final Vec3 MAGE_POS = new Vec3(0.5, -60, 0.5);

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!ENABLED || done) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        ServerPlayer mage = server.getPlayerList().getPlayerByName(HexFilm.MAGE);
        ServerPlayer rival = server.getPlayerList().getPlayerByName(HexFilm.RIVAL);
        ServerPlayer cam = server.getPlayerList().getPlayerByName(HexFilm.CAM);
        if (t0 < 0) {
            ready = mage != null && rival != null && cam != null ? ready + 1 : 0;
            if (ready == 100) {
                setup(server, level, mage, rival, cam);
                t0 = level.getGameTime() + 40;
                PacketDistributor.sendToAllPlayers(new HexFilm.Start(t0));
                LOGGER.info("[HexFilm] start at game time {}", t0);
            }
            return;
        }
        if (mage == null || rival == null) {
            LOGGER.warn("[HexFilm] a player left, stopping");
            finish(server);
            return;
        }
        int t = (int) (level.getGameTime() - t0);
        if (t == -20 || t == 0) PacketDistributor.sendToAllPlayers(new HexFilm.Start(t0)); // late joiners
        act(t, server, level, mage, rival);
        if (t >= HexFilm.END + 30) finish(server);
    }

    private static void finish(MinecraftServer server) {
        done = true;
        server.tickRateManager().setTickRate(20f);
        LOGGER.info("[HexFilm] done");
    }

    private static void setup(MinecraftServer server, ServerLevel level, ServerPlayer mage, ServerPlayer rival, ServerPlayer cam) {
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(false, server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        level.setDayTime(5000);
        level.setWeatherParameters(12000, 0, false, false);
        List<Entity> stray = new ArrayList<>();
        for (Entity e : level.getAllEntities()) if (!(e instanceof Player)) stray.add(e);
        stray.forEach(Entity::discard);

        mage.setGameMode(GameType.SURVIVAL);
        mage.getInventory().clearContent();
        select(mage, 0);
        mage.removeAllEffects();
        mage.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 3, false, false, false));
        mage.addEffect(new MobEffectInstance(MobEffects.SATURATION, -1, 0, false, false, false));
        mage.setHealth(mage.getMaxHealth());
        HexPlayerData d = HexCasting.data(mage);
        d.charges = HexConfig.MAX_CHARGES.get();
        java.util.Arrays.fill(d.cooldown, 0);
        d.passivesDisabled = 0;
        d.dirty = true;
        place(mage, MAGE_POS, 0f, 0f);

        rival.setGameMode(GameType.SURVIVAL);
        rival.getInventory().clearContent();
        rival.getInventory().setItem(0, new ItemStack(Items.BOW));
        rival.getInventory().setItem(8, new ItemStack(Items.ARROW, 64));
        select(rival, 0);
        rival.removeAllEffects();
        rival.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, -1, 4, false, false, false));
        rival.addEffect(new MobEffectInstance(MobEffects.SATURATION, -1, 0, false, false, false));
        rival.setHealth(rival.getMaxHealth());
        place(rival, new Vec3(0.5, -60, 12.5), 180f, 0f);

        cam.setGameMode(GameType.SPECTATOR);
        place(cam, new Vec3(0.5, -63.5, 0.5), 0f, 0f);

        server.tickRateManager().setTickRate(HexFilm.TICK_RATE);
    }

    private static void act(int t, MinecraftServer server, ServerLevel level, ServerPlayer mage, ServerPlayer rival) {
        switch (t) {
            case 10 -> {
                mage.getInventory().setItem(0, new ItemStack(com.hexmagic.registry.HexItems.HEX_STAFF.get()));
                select(mage, 0);
            }
            case 55 -> place(mage, MAGE_POS, 0f, 0f);
            case 66, 96 -> aimAt(rival, mage.getEyePosition().add(0, 0.2, 0));
            case 132 -> place(rival, new Vec3(0.5, -60, 5.9), 180f, 0f);
            case 175 -> {
                place(rival, new Vec3(-6.5, -60, -2.5), 0f, 0f);
                heal(rival);
            }
            case 184 -> aimAt(rival, mage.getEyePosition().add(0, 0.25, 0));
            case 255 -> {
                heal(rival);
                place(rival, new Vec3(0.5, -60, 11.5), 180f, 0f);
                for (int x = -2; x <= 3; x++) for (int y = -60; y <= -57; y++) level.setBlockAndUpdate(new BlockPos(x, y, 8), Blocks.STONE.defaultBlockState());
            }
            case 260, 300 -> aimAt(mage, rival.getBoundingBox().getCenter());
            case 392 -> {
                for (int x = -2; x <= 3; x++) for (int y = -60; y <= -57; y++) level.setBlockAndUpdate(new BlockPos(x, y, 8), Blocks.AIR.defaultBlockState());
                heal(rival);
                place(rival, new Vec3(0.5, -60, 9.5), 180f, 0f);
            }
            case 400 -> aimAt(mage, rival.getBoundingBox().getCenter());
            case 462 -> {
                heal(rival);
                place(mage, MAGE_POS, 0f, 0f);
            }
            case 545 -> place(mage, new Vec3(3.8, mage.getY() + 0.1, 0.5), 90f, 20f);
            case 618 -> {
                place(mage, MAGE_POS, 0f, 0f);
                heal(rival);
                place(rival, new Vec3(0.5, -60, 10.5), 180f, 0f);
            }
            case 634, 668 -> aimAt(mage, rival.getBoundingBox().getCenter());
            case 708 -> {
                heal(rival);
                place(rival, new Vec3(0.5, -60, 9.5), 180f, 0f);
            }
            case 714 -> {
                aimAt(mage, rival.getBoundingBox().getCenter());
                ensureCharges(mage, 2);
            }
            case 756 -> look(rival, 0f, 0f);
            case 790 -> look(rival, 90f, 0f);
            case 844 -> aimAt(mage, rival.getBoundingBox().getCenter());
            case 968 -> {
                heal(rival);
                rival.getInventory().setItem(1, new ItemStack(Items.NETHERITE_AXE));
                select(rival, 1);
                place(rival, new Vec3(0.5, -60, 4.8), 180f, 12f);
                place(mage, MAGE_POS, 0f, 0f);
                ensureCharges(mage, 1);
            }
            case 1000, 1024 -> look(rival, 180f, 12f);
            default -> {}
        }
    }

    private static void ensureCharges(ServerPlayer mage, int n) {
        HexPlayerData d = HexCasting.data(mage);
        if (d.charges < n) {
            LOGGER.warn("[HexFilm] topping up charges {} -> {}", d.charges, n);
            d.charges = n;
            d.dirty = true;
        }
        java.util.Arrays.fill(d.cooldown, 0);
    }

    private static void heal(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
    }

    private static void select(ServerPlayer p, int slot) {
        p.getInventory().selected = slot;
        p.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }

    private static void place(ServerPlayer p, Vec3 pos, float yaw, float pitch) {
        p.setDeltaMovement(Vec3.ZERO);
        p.teleportTo(p.serverLevel(), pos.x, pos.y, pos.z, yaw, pitch);
        p.setYHeadRot(yaw);
    }

    private static void look(ServerPlayer p, float yaw, float pitch) {
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch);
        p.setYHeadRot(yaw);
    }

    private static void aimAt(ServerPlayer p, Vec3 target) {
        Vec3 d = target.subtract(p.getEyePosition());
        float yaw = (float) (Math.atan2(-d.x, d.z) * 180.0 / Math.PI);
        float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI);
        look(p, yaw, pitch);
    }

    static boolean unused(HexAbility a) {
        return a == null;
    }
}
