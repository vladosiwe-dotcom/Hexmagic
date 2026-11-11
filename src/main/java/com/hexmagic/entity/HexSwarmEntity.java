package com.hexmagic.entity;

import com.hexmagic.HexConfig;
import com.hexmagic.ability.HexCasting;
import com.hexmagic.ability.HexTargeting;
import com.hexmagic.net.HexFx;
import com.hexmagic.registry.HexEntities;
import com.hexmagic.registry.HexItems;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hex Attack: a ring of small, already spinning drills circling above the caster. One entity
 * stands for the whole ring; a drill only becomes its own entity the moment it is fired.
 */
public class HexSwarmEntity extends Entity {
    public static final int ST_FORMING = 0;
    public static final int ST_ORBIT = 1;
    public static final int ST_AIMING = 2;
    public static final int ST_DISSOLVING = 3;

    public static final int FORM_TICKS = 22;
    public static final int AIM_TICKS = 16;
    public static final int FIRE_INTERVAL_TICKS = 7;
    public static final int DISSOLVE_TICKS = 12;
    public static final double ORBIT_RADIUS = 1.5;
    public static final double ORBIT_SPEED = 0.105;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(HexSwarmEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(HexSwarmEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> COUNT = SynchedEntityData.defineId(HexSwarmEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> FIRED = SynchedEntityData.defineId(HexSwarmEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(HexSwarmEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerUuid;
    private int sinceLastShot;
    private int staffAway;
    private boolean finished;

    public int stateTicks;
    /** 0 = circling freely, 1 = every tip locked onto the target. Smoothed on the client. */
    public float aim, aimO;

    public HexSwarmEntity(EntityType<? extends HexSwarmEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static HexSwarmEntity create(ServerLevel level, LivingEntity owner, int count) {
        HexSwarmEntity e = new HexSwarmEntity(HexEntities.SWARM.get(), level);
        e.ownerUuid = owner.getUUID();
        e.entityData.set(OWNER, owner.getId());
        e.entityData.set(COUNT, (byte) count);
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, (byte) ST_FORMING);
        builder.define(OWNER, -1);
        builder.define(COUNT, (byte) 5);
        builder.define(FIRED, 0);
        builder.define(TARGET, -1);
    }

    // ------------------------------------------------------------------ shared orbit math

    public static double slotAngle(int index, int count, double time) {
        return time * ORBIT_SPEED + index * (Math.PI * 2.0 / count);
    }

    /** World position of slot {@code index} for an owner standing at {@code feet}. */
    public static Vec3 slotPos(Vec3 feet, float ownerHeight, int index, int count, double time) {
        double a = slotAngle(index, count, time);
        double bob = Math.sin(time * 0.13 + index * 1.7) * 0.1;
        return new Vec3(feet.x + Math.cos(a) * ORBIT_RADIUS, feet.y + ownerHeight + 0.95 + bob, feet.z + Math.sin(a) * ORBIT_RADIUS);
    }

    /** Direction a circling drill points in: along its path, tipped slightly outward and up. */
    public static Vec3 slotHeading(int index, int count, double time) {
        double a = slotAngle(index, count, time);
        return new Vec3(-Math.sin(a) * 0.9 + Math.cos(a) * 0.15, 0.22, Math.cos(a) * 0.9 + Math.sin(a) * 0.15).normalize();
    }

    // ------------------------------------------------------------------ accessors

    public int getState() {
        return entityData.get(STATE);
    }

    private void setState(int state) {
        if (getState() != state) {
            entityData.set(STATE, (byte) state);
            stateTicks = 0;
        }
    }

    public int getCount() {
        return entityData.get(COUNT);
    }

    public int getFiredMask() {
        return entityData.get(FIRED);
    }

    public boolean isFired(int index) {
        return (getFiredMask() & (1 << index)) != 0;
    }

    public int remaining() {
        return getCount() - Integer.bitCount(getFiredMask());
    }

    public int getTargetId() {
        return entityData.get(TARGET);
    }

    public int getOwnerIdClient() {
        return entityData.get(OWNER);
    }

    public boolean isReady() {
        return getState() == ST_ORBIT;
    }

    @Nullable
    public Entity getOwner() {
        Entity e = level().getEntity(entityData.get(OWNER));
        if (e == null && ownerUuid != null && level() instanceof ServerLevel sl) {
            e = sl.getEntity(ownerUuid);
            if (e != null) entityData.set(OWNER, e.getId());
        }
        return e;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        stateTicks++;
        if (level().isClientSide) {
            aimO = aim;
            float want = getState() == ST_AIMING ? 1f : 0f;
            aim += (want - aim) * 0.22f;
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Entity raw = getOwner();
        if (!(raw instanceof LivingEntity owner) || !owner.isAlive() || owner.level() != level) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        if (getState() != ST_DISSOLVING) {
            staffAway = HexItems.isHoldingStaff(owner) ? 0 : staffAway + 1;
            if (staffAway > 100 || tickCount > 20 * 120) {
                dissolve();
                return;
            }
        }
        switch (getState()) {
            case ST_FORMING -> {
                if (stateTicks >= FORM_TICKS) setState(ST_ORBIT);
            }
            case ST_ORBIT -> {
                if (stateTicks % 40 == 0) {
                    level.playSound(null, getX(), getY() + 2.6, getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.25f, 1.9f);
                }
            }
            case ST_AIMING -> tickVolley(level, owner);
            case ST_DISSOLVING -> {
                if (stateTicks >= DISSOLVE_TICKS) discard();
            }
            default -> discard();
        }
    }

    /** Starts the volley. Drills leave one at a time, never all at once. */
    public boolean beginVolley(LivingEntity target) {
        if (getState() != ST_ORBIT || remaining() <= 0) return false;
        entityData.set(TARGET, target.getId());
        sinceLastShot = 0;
        setState(ST_AIMING);
        if (level() instanceof ServerLevel sl) {
            HexFx.send(sl, HexFx.TARGET_LOCK, target.getBoundingBox().getCenter(), Vec3.ZERO, AIM_TICKS + remaining() * FIRE_INTERVAL_TICKS, 0f, target.getId());
            sl.playSound(null, getX(), getY() + 2.6, getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.6f);
        }
        return true;
    }

    private void tickVolley(ServerLevel level, LivingEntity owner) {
        Entity raw = level.getEntity(getTargetId());
        LivingEntity target = raw instanceof LivingEntity l && l.isAlive() && l.level() == level ? l : null;
        if (target == null || target.distanceToSqr(owner) > sq(HexConfig.HEX_ATTACK_RANGE.get() * 1.6)) {
            target = HexTargeting.findAutoTarget(owner, HexConfig.HEX_ATTACK_RANGE.get());
            if (target == null) {
                // Nothing left to shoot at: the remaining drills go back to circling.
                entityData.set(TARGET, -1);
                setState(ST_ORBIT);
                return;
            }
            entityData.set(TARGET, target.getId());
            HexFx.send(level, HexFx.TARGET_LOCK, target.getBoundingBox().getCenter(), Vec3.ZERO, remaining() * FIRE_INTERVAL_TICKS + 6, 0f, target.getId());
        }
        if (stateTicks < AIM_TICKS) return;
        if (sinceLastShot > 0) {
            sinceLastShot--;
            return;
        }
        int count = getCount();
        int next = -1;
        // Fire the drill that is currently best placed, so every shot starts from a different spot.
        double best = -Double.MAX_VALUE;
        double time = level.getGameTime();
        Vec3 feet = owner.position();
        Vec3 aimAt = target.getBoundingBox().getCenter();
        for (int i = 0; i < count; i++) {
            if (isFired(i)) continue;
            Vec3 slot = slotPos(feet, owner.getBbHeight(), i, count, time);
            double score = -slot.distanceToSqr(aimAt) + ((i * 7919) % 5) * 0.01;
            if (score > best) {
                best = score;
                next = i;
            }
        }
        if (next < 0) {
            finish(owner);
            return;
        }
        Vec3 slot = slotPos(feet, owner.getBbHeight(), next, count, time);
        Vec3 heading = slotHeading(next, count, time);
        Vec3 toTarget = aimAt.subtract(slot).normalize();
        // Leave along the orbit and curl towards the target: each drill draws its own arc.
        Vec3 dir = heading.scale(0.55).add(toTarget.scale(0.45)).add(0, 0.35, 0).normalize();
        HexDrillEntity drill = HexDrillEntity.createMini(level, owner, slot, dir, target);
        level.addFreshEntity(drill);
        entityData.set(FIRED, getFiredMask() | (1 << next));
        sinceLastShot = FIRE_INTERVAL_TICKS;
        level.playSound(null, slot.x, slot.y, slot.z, com.hexmagic.registry.HexSounds.SWARM_FIRE.get(), SoundSource.PLAYERS, 1.0f, 0.9f + next * 0.08f);
        if (remaining() <= 0) finish(owner);
    }

    private void finish(LivingEntity owner) {
        if (finished) return;
        finished = true;
        if (owner instanceof ServerPlayer sp) HexCasting.onSwarmFinished(sp, this);
        setState(ST_DISSOLVING);
    }

    public void dissolve() {
        if (getState() == ST_DISSOLVING) return;
        Entity owner = getOwner();
        if (!finished && owner instanceof ServerPlayer sp) {
            finished = true;
            HexCasting.onSwarmFinished(sp, this);
        }
        setState(ST_DISSOLVING);
    }

    private static double sq(double v) {
        return v * v;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && STATE.equals(key)) stateTicks = 0;
    }

    // ------------------------------------------------------------------ entity plumbing

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3.0, 4.0, 3.0);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
