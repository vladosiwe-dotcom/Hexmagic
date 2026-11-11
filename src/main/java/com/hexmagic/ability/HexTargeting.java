package com.hexmagic.ability;

import com.hexmagic.registry.HexTags;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HexTargeting {
    private HexTargeting() {}

    /** Same team, or one is the other's pet. */
    public static boolean areAllied(Entity a, Entity b) {
        if (a == b) return true;
        if (a.isAlliedTo(b) || b.isAlliedTo(a)) return true;
        if (b instanceof OwnableEntity o && a.getUUID().equals(o.getOwnerUUID())) return true;
        return a instanceof OwnableEntity o2 && b.getUUID().equals(o2.getOwnerUUID());
    }

    public static boolean isValidVictim(LivingEntity owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) return false;
        if (e instanceof Player p && (p.isCreative() || (owner instanceof Player op && !op.canHarmPlayer(p)))) return false;
        return !e.isInvulnerable() && !areAllied(owner, e);
    }

    /** The living entity under the crosshair, or null. Blocks in the way stop the search. */
    @Nullable
    public static LivingEntity pickLiving(LivingEntity owner, double range, double inflate) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = owner.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        double max = range;
        if (block.getType() != HitResult.Type.MISS) {
            max = block.getLocation().distanceTo(eye);
            end = block.getLocation();
        }
        AABB box = owner.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0 + inflate);
        LivingEntity best = null;
        double bestDist = max * max;
        for (Entity e : owner.level().getEntities(owner, box, x -> x instanceof LivingEntity l && isValidVictim(owner, l))) {
            AABB bb = e.getBoundingBox().inflate(inflate);
            double d;
            if (bb.contains(eye)) d = 0;
            else {
                var hit = bb.clip(eye, end);
                if (hit.isEmpty()) continue;
                d = hit.get().distanceToSqr(eye);
            }
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) e;
            }
        }
        if (best == null) {
            // Fall back to vanilla picking so a precise aim always works.
            EntityHitResult r = ProjectileUtil.getEntityHitResult(owner, eye, end, box, x -> x instanceof LivingEntity l && isValidVictim(owner, l), max * max);
            if (r != null) best = (LivingEntity) r.getEntity();
        }
        return best;
    }

    /**
     * Target for the automatic Hex Attack: only things that are actually fighting the caster or are
     * hostile by nature, in range and in sight. Passive and neutral creatures are never picked.
     */
    @Nullable
    public static LivingEntity findAutoTarget(LivingEntity owner, double range) {
        List<LivingEntity> list = owner.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range),
                e -> isValidVictim(owner, e) && isHostileTo(owner, e));
        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : list) {
            double d = e.distanceToSqr(owner);
            if (d >= bestDist || !owner.hasLineOfSight(e)) continue;
            bestDist = d;
            best = e;
        }
        return best;
    }

    public static boolean isHostileTo(LivingEntity owner, LivingEntity e) {
        if (e.getType().is(HexTags.HEX_ATTACK_IGNORED)) return false;
        if (e instanceof Mob mob && mob.getTarget() == owner) return true;
        if (e == owner.getLastHurtByMob() && owner.tickCount - owner.getLastHurtByMobTimestamp() < 300) return true;
        if (e == owner.getLastHurtMob() && owner.tickCount - owner.getLastHurtMobTimestamp() < 300) return true;
        if (e instanceof Player) return false;
        // Hostile by nature, but only when it is not sitting there peacefully (e.g. a tamed or leashed mob).
        return e instanceof Enemy && !(e instanceof Mob m && (m.isNoAi() || m.isLeashed()));
    }
}
