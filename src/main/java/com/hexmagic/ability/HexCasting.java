package com.hexmagic.ability;

import com.hexmagic.HexConfig;
import com.hexmagic.compat.IronsCompat;
import com.hexmagic.entity.HexConstructEntity;
import com.hexmagic.entity.HexDrillEntity;
import com.hexmagic.entity.HexPrisonEntity;
import com.hexmagic.entity.HexSwarmEntity;
import com.hexmagic.net.HexFx;
import com.hexmagic.net.SyncStatePayload;
import com.hexmagic.registry.HexAttachments;
import com.hexmagic.registry.HexItems;
import com.hexmagic.util.HexBasis;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Server-side brain of the Hex Staff: charges, cooldowns, passives and the entry point of every
 * ability. Entities carry out the abilities; this class decides whether they may start.
 */
public final class HexCasting {
    private HexCasting() {}

    public static final int SWARM_FORM_DELAY_TICKS = 60;
    public static final int SAFE_LANDING_WINDOW_TICKS = 600;
    public static final double CONSTRUCT_REACH = 14.0;

    public static HexPlayerData data(ServerPlayer player) {
        HexPlayerData d = player.getData(HexAttachments.PLAYER_DATA);
        if (d.charges < 0) d.charges = HexConfig.MAX_CHARGES.get();
        return d;
    }

    // ------------------------------------------------------------------ input

    public static void press(ServerPlayer player, HexAbility ability) {
        if (!HexItems.isHoldingStaff(player) || player.isSpectator()) return;
        HexPlayerData d = data(player);
        long now = player.level().getGameTime();
        // One physical press can reach the server twice (key + item use); the second one is dropped.
        if (d.lastPressTick == now) return;
        d.lastPressTick = now;
        switch (ability) {
            case SHIELD -> castShield(player, d);
            case DRILL -> startDrill(player, d);
            case SPIKE -> castSpike(player, d);
            case LAUNCH -> castLaunch(player, d);
            case REFLECTION -> {}
            case HEX_ATTACK -> castHexAttack(player, d);
            case PRISON -> castPrison(player, d);
        }
    }

    public static void release(ServerPlayer player, HexAbility ability) {
        if (ability == HexAbility.DRILL) releaseDrill(player);
    }

    public static void select(ServerPlayer player, int index) {
        if (!HexAbility.isValidSelection(index, IronsCompat.LOADED)) return;
        HexPlayerData d = data(player);
        d.selected = index;
        d.dirty = true;
    }

    private static void markCast(ServerPlayer player, HexPlayerData d) {
        d.idleTicks = 0;
        d.dirty = true;
    }

    private static boolean spendCharge(ServerPlayer player, HexPlayerData d, int amount) {
        if (d.charges < amount) {
            deny(player, "message.hexmagic.no_charges");
            return false;
        }
        if (d.charges == HexConfig.MAX_CHARGES.get()) d.chargeRegen = 0;
        d.charges -= amount;
        d.dirty = true;
        return true;
    }

    private static boolean checkCooldown(ServerPlayer player, HexPlayerData d, HexAbility ability) {
        if (d.ready(ability)) return true;
        deny(player, "message.hexmagic.cooldown", ability.displayName(), String.format("%.1f", d.cooldown[ability.ordinal()] / 20f));
        return false;
    }

    private static void cooldown(ServerPlayer player, HexPlayerData d, HexAbility ability, int ticks) {
        d.startCooldown(ability, Math.max(0, Math.round(ticks * IronsCompat.cooldownMultiplier(player))));
    }

    private static void deny(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
    }

    private static int formTicks(ServerPlayer player, HexPlayerData d, int base) {
        return d.onPlatform ? Math.max(2, Math.round(base / HexConfig.PLATFORM_CAST_BOOST.get().floatValue())) : base;
    }

    // ------------------------------------------------------------------ 1. Hexa Shield

    private static void castShield(ServerPlayer player, HexPlayerData d) {
        if (!spendCharge(player, d, 1)) return;
        ServerLevel level = player.serverLevel();
        HexConstructEntity wall = spawnWall(level, player, d, formTicks(player, d, 14));
        level.addFreshEntity(wall);
        level.playSound(null, wall.getX(), wall.getY(), wall.getZ(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1.4f, 0.75f);
        level.playSound(null, wall.getX(), wall.getY(), wall.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.6f);
        markCast(player, d);
    }

    /** A vertical wall about two blocks in front of the caster, standing on the ground. */
    private static HexConstructEntity spawnWall(ServerLevel level, ServerPlayer player, HexPlayerData d, int formTicks) {
        float halfW = HexConfig.SHIELD_WIDTH.get().floatValue() * 0.5f;
        float halfH = HexConfig.SHIELD_HEIGHT.get().floatValue() * 0.5f;
        float yaw = player.getYRot();
        HexBasis b = HexBasis.of(yaw, 0f);
        Vec3 eye = player.getEyePosition();
        double dist = 2.2;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(b.f().scale(dist + 0.6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) dist = Math.max(0.9, hit.getLocation().distanceTo(eye) - 0.6);
        double cx = player.getX() + b.f().x * dist, cz = player.getZ() + b.f().z * dist;
        double cy = player.getY() + halfH - 0.15;
        // Let the wall sit on a ledge slightly below or above the caster.
        BlockPos under = BlockPos.containing(cx, player.getY() - 0.1, cz);
        if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
            BlockPos below = under.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) cy -= 1.0;
        }
        return HexConstructEntity.create(level, HexConstructEntity.KIND_SHIELD, player, new Vec3(cx, cy, cz), yaw, 0f, halfW, halfH, formTicks,
                HexConfig.SHIELD_LIFETIME_TICKS.get());
    }

    // ------------------------------------------------------------------ 2. Hexa Drill

    private static void startDrill(ServerPlayer player, HexPlayerData d) {
        ServerLevel level = player.serverLevel();
        Entity existing = level.getEntity(d.drillId);
        if (existing instanceof HexDrillEntity drill && drill.isAlive() && drill.getState() == HexDrillEntity.ST_CHARGING) return;
        if (!checkCooldown(player, d, HexAbility.DRILL)) return;
        HexDrillEntity drill = HexDrillEntity.createCharging(level, player);
        level.addFreshEntity(drill);
        d.drillId = drill.getId();
        level.playSound(null, player.getX(), player.getY() + 2, player.getZ(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1.2f, 0.6f);
        markCast(player, d);
    }

    public static void releaseDrill(ServerPlayer player) {
        HexPlayerData d = data(player);
        Entity e = player.serverLevel().getEntity(d.drillId);
        if (!(e instanceof HexDrillEntity drill) || drill.getState() != HexDrillEntity.ST_CHARGING) return;
        boolean fired = drill.launch(player);
        d.drillId = -1;
        if (fired) {
            cooldown(player, d, HexAbility.DRILL, HexConfig.DRILL_COOLDOWN_TICKS.get());
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        }
        markCast(player, d);
    }

    // ------------------------------------------------------------------ 3. Hexa Spike

    private static void castSpike(ServerPlayer player, HexPlayerData d) {
        if (!checkCooldown(player, d, HexAbility.SPIKE)) return;
        ServerLevel level = player.serverLevel();
        HexConstructEntity wall = findOwnWallUnderCrosshair(player, w -> w.canBecomeSpike());
        if (wall == null) {
            // No wall to transform: raise a fresh one and let it grow its spikes as it forms.
            if (!spendCharge(player, d, 1)) return;
            wall = spawnWall(level, player, d, formTicks(player, d, 10));
            level.addFreshEntity(wall);
        }
        wall.beginSpike();
        cooldown(player, d, HexAbility.SPIKE, HexConfig.SPIKE_COOLDOWN_TICKS.get());
        markCast(player, d);
    }

    @Nullable
    public static HexConstructEntity findOwnWallUnderCrosshair(ServerPlayer player, java.util.function.Predicate<HexConstructEntity> filter) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(CONSTRUCT_REACH));
        HexConstructEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (HexConstructEntity c : player.serverLevel().getEntitiesOfClass(HexConstructEntity.class, new AABB(eye, end).inflate(6.0))) {
            if (!c.isOwnedBy(player) || !filter.test(c)) continue;
            Vector3f hit = c.intersect(eye, end, 0.3);
            if (hit != null && hit.z < bestT) {
                bestT = hit.z;
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ 4. Hexa Launch

    private static void castLaunch(ServerPlayer player, HexPlayerData d) {
        if (!d.passivesEnabled()) {
            deny(player, "message.hexmagic.passives_disabled", String.format("%.0f", d.passivesDisabled / 20f));
            return;
        }
        if (!checkCooldown(player, d, HexAbility.LAUNCH)) return;
        ServerLevel level = player.serverLevel();
        Entity existing = level.getEntity(d.platformId);
        if (existing instanceof HexConstructEntity p && p.isAlive() && !p.isBroken()) {
            if (p.getState() == HexConstructEntity.ST_RISING) return;
            p.dissolve();
        }
        if (!spendCharge(player, d, 1)) return;
        float half = HexConfig.PLATFORM_SIZE.get().floatValue() * 0.5f;
        Vec3 center = new Vec3(player.getX(), player.getY() - 0.02, player.getZ());
        HexConstructEntity platform = HexConstructEntity.create(level, HexConstructEntity.KIND_PLATFORM, player, center, 0f, 0f, half, half, 4,
                HexConfig.PLATFORM_LIFETIME_TICKS.get());
        level.addFreshEntity(platform);
        platform.beginRise(player.getY() + HexConfig.LAUNCH_HEIGHT.get());
        d.platformId = platform.getId();
        d.safeLandingTicks = SAFE_LANDING_WINDOW_TICKS;
        d.cushionShown = false;
        cooldown(player, d, HexAbility.LAUNCH, 40);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1.4f, 0.9f);
        markCast(player, d);
    }

    public static boolean isOnOwnPlatform(ServerPlayer player) {
        return data(player).onPlatform;
    }

    // ------------------------------------------------------------------ 6. Hex Attack

    private static void castHexAttack(ServerPlayer player, HexPlayerData d) {
        if (!d.passivesEnabled()) {
            deny(player, "message.hexmagic.passives_disabled", String.format("%.0f", d.passivesDisabled / 20f));
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity e = level.getEntity(d.swarmId);
        if (e instanceof HexSwarmEntity swarm && swarm.isAlive()) {
            if (!swarm.isReady()) return;
            LivingEntity target = HexTargeting.pickLiving(player, HexConfig.HEX_ATTACK_RANGE.get(), 0.6);
            if (target == null) target = HexTargeting.findAutoTarget(player, HexConfig.HEX_ATTACK_RANGE.get());
            if (target == null) {
                deny(player, "message.hexmagic.no_target");
                return;
            }
            swarm.beginVolley(target);
            markCast(player, d);
            return;
        }
        if (!checkCooldown(player, d, HexAbility.HEX_ATTACK)) return;
        summonSwarm(player, d);
        markCast(player, d);
    }

    private static void summonSwarm(ServerPlayer player, HexPlayerData d) {
        ServerLevel level = player.serverLevel();
        HexSwarmEntity swarm = HexSwarmEntity.create(level, player, HexConfig.HEX_ATTACK_DRILLS.get());
        level.addFreshEntity(swarm);
        d.swarmId = swarm.getId();
        d.dirty = true;
        level.playSound(null, player.getX(), player.getY() + 2, player.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.0f, 1.3f);
    }

    public static void onSwarmFinished(ServerPlayer player, HexSwarmEntity swarm) {
        HexPlayerData d = data(player);
        if (d.swarmId == swarm.getId()) d.swarmId = -1;
        cooldown(player, d, HexAbility.HEX_ATTACK, HexConfig.HEX_ATTACK_COOLDOWN_TICKS.get());
        d.idleTicks = 0;
    }

    // ------------------------------------------------------------------ 7. Hex Prison

    private static void castPrison(ServerPlayer player, HexPlayerData d) {
        if (!checkCooldown(player, d, HexAbility.PRISON)) return;
        ServerLevel level = player.serverLevel();
        Entity existing = level.getEntity(d.prisonId);
        if (existing instanceof HexPrisonEntity p && p.isAlive()) {
            deny(player, "message.hexmagic.prison_active");
            return;
        }
        LivingEntity target = HexTargeting.pickLiving(player, HexConfig.PRISON_RANGE.get(), 0.8);
        if (target == null) {
            deny(player, "message.hexmagic.no_target");
            return;
        }
        if (!spendCharge(player, d, HexConfig.PRISON_CHARGE_COST.get())) return;
        HexPrisonEntity prison = HexPrisonEntity.create(level, player, target);
        level.addFreshEntity(prison);
        d.prisonId = prison.getId();
        cooldown(player, d, HexAbility.PRISON, HexConfig.PRISON_COOLDOWN_TICKS.get());
        HexFx.send(level, HexFx.PRISON_PREP, player.position(), player.getLookAngle(), HexPrisonEntity.PREP_TICKS, 0f, player.getId());
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.8f, 1.4f);
        markCast(player, d);
    }

    // ------------------------------------------------------------------ passives / per-tick

    public static void onProtectiveFieldDestroyed(ServerPlayer player) {
        HexPlayerData d = data(player);
        d.passivesDisabled = HexConfig.PASSIVE_LOCKOUT_TICKS.get();
        d.safeLandingTicks = 0;
        d.dirty = true;
        Entity e = player.serverLevel().getEntity(d.swarmId);
        if (e instanceof HexSwarmEntity swarm) swarm.dissolve();
        player.displayClientMessage(Component.translatable("message.hexmagic.field_destroyed", HexConfig.PASSIVE_LOCKOUT_TICKS.get() / 20)
                .withStyle(ChatFormatting.RED), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    public static void tick(ServerPlayer player) {
        HexPlayerData d = data(player);
        ServerLevel level = player.serverLevel();
        boolean holding = HexItems.isHoldingStaff(player) && !player.isSpectator();
        boolean changed = false;

        int maxCharges = HexConfig.MAX_CHARGES.get();
        int regenMax = HexConfig.CHARGE_RECHARGE_TICKS.get();
        if (d.charges < maxCharges) {
            if (++d.chargeRegen >= regenMax) {
                d.chargeRegen = 0;
                d.charges++;
                changed = true;
                if (holding) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.8f);
            }
        } else {
            d.chargeRegen = 0;
        }
        for (int i = 0; i < HexAbility.COUNT; i++) {
            if (d.cooldown[i] > 0 && --d.cooldown[i] == 0) changed = true;
        }
        if (d.passivesDisabled > 0 && --d.passivesDisabled == 0) {
            changed = true;
            player.displayClientMessage(Component.translatable("message.hexmagic.passives_restored").withStyle(ChatFormatting.AQUA), true);
        }
        if (d.safeLandingTicks > 0) d.safeLandingTicks--;

        // ---- platform contact
        Entity platformRaw = level.getEntity(d.platformId);
        boolean onPlatform = false;
        if (platformRaw instanceof HexConstructEntity platform && platform.isAlive() && !platform.isBroken()) {
            AABB top = platform.getBoundingBox();
            onPlatform = player.getY() <= top.maxY + 0.3 && player.getY() >= top.maxY - 0.6
                    && player.getX() >= top.minX - 0.3 && player.getX() <= top.maxX + 0.3
                    && player.getZ() >= top.minZ - 0.3 && player.getZ() <= top.maxZ + 0.3;
        } else if (d.platformId != -1) {
            d.platformId = -1;
        }
        if (onPlatform != d.onPlatform) {
            d.onPlatform = onPlatform;
            changed = true;
        }

        // ---- safe landing cushion
        if (d.safeLandingTicks > 0 && !d.cushionShown && !onPlatform && player.fallDistance > 2.5f && player.getDeltaMovement().y < -0.3) {
            Vec3 feet = player.position();
            BlockHitResult ground = level.clip(new ClipContext(feet, feet.add(0, -3.2, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            if (ground.getType() != HitResult.Type.MISS) {
                d.cushionShown = true;
                Vec3 at = ground.getLocation();
                HexFx.send(level, HexFx.CUSHION, new Vec3(player.getX(), at.y + 0.08, player.getZ()), new Vec3(0, 1, 0), 1.9f, 0f, player.getId());
                level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1.0f, 1.1f);
            }
        }

        // ---- Hex Attack: the ring of drills waits above the caster and fires on its own when idle
        if (holding) {
            d.idleTicks++;
            d.staffAwayTicks = 0;
        } else {
            d.staffAwayTicks++;
        }
        Entity swarmRaw = level.getEntity(d.swarmId);
        HexSwarmEntity swarm = swarmRaw instanceof HexSwarmEntity s && s.isAlive() && s.getState() != HexSwarmEntity.ST_DISSOLVING ? s : null;
        if (swarm == null && d.swarmId != -1) {
            d.swarmId = -1;
            changed = true;
        }
        if (holding && d.passivesEnabled()) {
            if (swarm == null && d.ready(HexAbility.HEX_ATTACK) && d.idleTicks >= SWARM_FORM_DELAY_TICKS && !player.isDeadOrDying()) {
                summonSwarm(player, d);
                changed = true;
            } else if (swarm != null && swarm.isReady() && d.idleTicks >= HexConfig.HEX_ATTACK_IDLE_TICKS.get()) {
                LivingEntity target = HexTargeting.findAutoTarget(player, HexConfig.HEX_ATTACK_RANGE.get());
                if (target != null) {
                    swarm.beginVolley(target);
                    d.idleTicks = 0;
                    changed = true;
                } else if (d.idleTicks % 40 == 0) {
                    // Nothing hostile nearby: keep waiting, but do not spam the search every tick.
                    d.idleTicks -= 1;
                }
            }
        }

        // ---- stale links
        if (d.drillId != -1 && !(level.getEntity(d.drillId) instanceof HexDrillEntity dr && dr.isAlive() && dr.getState() == HexDrillEntity.ST_CHARGING)) {
            d.drillId = -1;
        }
        if (d.prisonId != -1 && !(level.getEntity(d.prisonId) instanceof HexPrisonEntity pr && pr.isAlive())) d.prisonId = -1;

        if (changed || d.dirty || player.tickCount % 20 == 0) {
            d.dirty = false;
            SyncStatePayload payload = SyncStatePayload.of(d, maxCharges, regenMax, HexConfig.HEX_ATTACK_IDLE_TICKS.get(), swarm != null && swarm.isReady());
            if (player.connection != null && player.connection.hasChannel(payload)) PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Player attacked one of the constructs under their crosshair (sent by the client). */
    public static void attackConstruct(ServerPlayer player, int entityId) {
        if (!(player.serverLevel().getEntity(entityId) instanceof HexConstructEntity wall) || !wall.isSolid()) return;
        if (player.isSpectator() || wall.isFriendly(player)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 1.5));
        Vector3f hit = wall.intersect(eye, end, 0.25);
        if (hit == null) return;
        if (player.getAttackStrengthScale(0.5f) < 0.8f) return;
        float amount = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        player.resetAttackStrengthTicker();
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        wall.damage(amount, wall.localToWorld(hit.x, hit.y), player);
    }

    /** Rough damage a projectile would deal, used to cost barrier durability for stopping it. */
    public static float estimateProjectileDamage(Projectile p) {
        float irons = IronsCompat.projectileDamage(p);
        if (irons >= 0) return Math.max(0.5f, irons);
        if (p instanceof AbstractArrow arrow) {
            return (float) Mth.clamp(Math.ceil(arrow.getDeltaMovement().length() * arrow.getBaseDamage()), 1.0, 40.0);
        }
        if (p instanceof LargeFireball) return 6f;
        if (p instanceof WitherSkull) return 8f;
        if (p instanceof Fireball) return 5f;
        if (p instanceof ThrownPotion) return 1f;
        return 1f;
    }

    /** Clears every live link when the player leaves, dies or changes dimension. */
    public static void cleanup(ServerPlayer player, boolean keepConstructs) {
        HexPlayerData d = data(player);
        ServerLevel level = player.serverLevel();
        if (level.getEntity(d.drillId) instanceof HexDrillEntity drill && drill.getState() == HexDrillEntity.ST_CHARGING) drill.fizzle();
        if (level.getEntity(d.swarmId) instanceof HexSwarmEntity swarm) swarm.dissolve();
        if (level.getEntity(d.prisonId) instanceof HexPrisonEntity prison) prison.release();
        if (!keepConstructs && level.getEntity(d.platformId) instanceof HexConstructEntity platform) platform.dissolve();
        d.drillId = d.swarmId = d.prisonId = d.platformId = -1;
        d.onPlatform = false;
        d.safeLandingTicks = 0;
        d.dirty = true;
    }
}
