package com.hexmagic.event;

import com.hexmagic.HexConfig;
import com.hexmagic.HexMagic;
import com.hexmagic.ability.HexAbility;
import com.hexmagic.ability.HexCasting;
import com.hexmagic.ability.HexPlayerData;
import com.hexmagic.ability.HexTargeting;
import com.hexmagic.compat.IronsCompat;
import com.hexmagic.entity.HexConstructEntity;
import com.hexmagic.net.HexFx;
import com.hexmagic.registry.HexDamage;
import com.hexmagic.registry.HexItems;
import com.hexmagic.registry.HexTags;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.hexmagic.item.HexStaffLogic;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HexMagic.MOD_ID)
public final class HexServerEvents {
    private HexServerEvents() {}

    private static final String REFLECTED_TAG = "hexmagic_reflected";

    /**
     * The right mouse button belongs to the selected Hex ability unless the player handed it to
     * Iron's Spells 'n Spellbooks. Runs before Iron's own handler so the two never both fire.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!HexItems.isStaff(event.getItemStack())) return;
        int selected = HexStaffLogic.selection(player.level(), player);
        HexAbility ability = HexAbility.byIndex(selected);
        if (ability == null) return; // SELECT_IRONS: let Iron's cast
        if (player instanceof ServerPlayer sp) HexCasting.press(sp, ability);
        player.startUsingItem(event.getHand());
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer sp) HexCasting.tick(sp);
    }

    // ------------------------------------------------------------------ barriers take hits meant for those behind them

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(victim.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        if (HexDamage.isHexDamage(source) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypeTags.IS_EXPLOSION) && source.getDirectEntity() == null) {
            return;
        }
        if (source.getDirectEntity() instanceof Projectile) return; // projectiles are stopped in flight instead
        Vec3 from = source.getSourcePosition();
        if (from == null) return;
        Vec3 to = victim.getBoundingBox().getCenter();
        if (from.distanceToSqr(to) < 0.01) return;
        Entity attacker = source.getEntity();
        for (HexConstructEntity wall : level.getEntitiesOfClass(HexConstructEntity.class, new AABB(from, to).inflate(4.0))) {
            if (!wall.isWall()) continue;
            if (wall.tryAbsorb(from, to, event.getAmount(), attacker)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 5. Hexa Reflection

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || hit.getType() != HitResult.Type.ENTITY) return;
        if (!(hit.getEntity() instanceof ServerPlayer player)) return;
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide || !(projectile.level() instanceof ServerLevel level)) return;
        if (!HexItems.isHoldingStaff(player) || player.isSpectator()) return;
        HexPlayerData d = HexCasting.data(player);
        if (!d.passivesEnabled() || !d.ready(HexAbility.REFLECTION)) return;
        if (projectile.getType().is(HexTags.NOT_REFLECTABLE)) return;
        if (projectile.getPersistentData().getBoolean(REFLECTED_TAG)) return;
        Entity shooter = projectile.getOwner();
        if (shooter == player || (shooter != null && HexTargeting.areAllied(player, shooter))) return;

        Vec3 vel = projectile.getDeltaMovement();
        double speed = vel.length();
        if (speed < 0.05) return;
        Vec3 incoming = vel.scale(1.0 / speed);

        // Aim back at the one who fired it when they are still around; otherwise straight back.
        Vec3 outgoing = incoming.scale(-1);
        // Vanilla reports the victim's feet as the hit point, so work out where the projectile really
        // met the player: at its own height, on the side it came from.
        double hitY = Mth.clamp(projectile.getY(), player.getY() + 0.3, player.getY() + player.getBbHeight() - 0.1);
        double reach = player.getBbWidth() * 0.5 + 0.35;
        Vec3 origin = new Vec3(player.getX() - incoming.x * reach, hitY, player.getZ() - incoming.z * reach);
        if (shooter instanceof LivingEntity living && living.isAlive() && living.level() == level && living.distanceToSqr(player) < 64 * 64) {
            Vec3 aim = living.getEyePosition().subtract(0, living.getBbHeight() * 0.2, 0).subtract(origin);
            if (aim.lengthSqr() > 1.0e-4) outgoing = aim.normalize();
        }

        float factor = HexConfig.REFLECT_DAMAGE_FACTOR.get().floatValue();
        boolean scaled = IronsCompat.onReflect(projectile, shooter, factor);
        if (!scaled && projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(arrow.getBaseDamage() * factor);
        }
        projectile.getPersistentData().putBoolean(REFLECTED_TAG, true);
        projectile.setOwner(player);
        projectile.setDeltaMovement(outgoing.scale(speed));
        Vec3 pos = origin.add(outgoing.scale(0.35));
        projectile.setPos(pos.x, pos.y, pos.z);
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
        float yaw = (float) (Math.atan2(-outgoing.x, outgoing.z) * 180.0 / Math.PI);
        float pitch = (float) (-Math.atan2(outgoing.y, outgoing.horizontalDistance()) * 180.0 / Math.PI);
        projectile.setYRot(yaw);
        projectile.setXRot(pitch);
        projectile.yRotO = yaw;
        projectile.xRotO = pitch;
        event.setCanceled(true);

        d.startCooldown(HexAbility.REFLECTION, Math.round(HexConfig.REFLECT_COOLDOWN_TICKS.get() * IronsCompat.cooldownMultiplier(player)));
        HexFx.send(level, HexFx.REFLECT, origin, incoming, 1.0f, 0f, player.getId());
        HexFx.send(level, HexFx.IMPACT, origin, incoming, 0.6f, 0f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2f, 1.5f);
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
    }

    // ------------------------------------------------------------------ Hexa Launch: landing cushion

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        HexPlayerData d = HexCasting.data(player);
        if (d.safeLandingTicks <= 0 && !d.onPlatform) return;
        if (event.getDistance() <= 3f) return;
        ServerLevel level = player.serverLevel();
        if (!d.cushionShown) {
            HexFx.send(level, HexFx.CUSHION, new Vec3(player.getX(), player.getY() + 0.05, player.getZ()), new Vec3(0, 1, 0), 1.9f, 1f, player.getId());
        } else {
            HexFx.send(level, HexFx.CUSHION, new Vec3(player.getX(), player.getY() + 0.05, player.getZ()), new Vec3(0, 1, 0), 1.9f, 2f, player.getId());
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.1f, 0.9f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
        event.setCanceled(true);
        d.safeLandingTicks = 0;
        d.cushionShown = false;
        d.dirty = true;
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HexCasting.cleanup(sp, true);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HexCasting.cleanup(sp, true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HexCasting.cleanup(sp, true);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) HexCasting.data(sp).dirty = true;
    }

    @Nullable
    private static Vec3 center(Entity e) {
        return e == null ? null : e.getBoundingBox().getCenter();
    }
}
