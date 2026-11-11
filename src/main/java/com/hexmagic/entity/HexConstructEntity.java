package com.hexmagic.entity;

import com.hexmagic.HexConfig;
import com.hexmagic.ability.HexCasting;
import com.hexmagic.ability.HexTargeting;
import com.hexmagic.compat.IronsCompat;
import com.hexmagic.net.HexFx;
import com.hexmagic.registry.HexDamage;
import com.hexmagic.registry.HexEntities;
import com.hexmagic.util.HexBasis;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A flat honeycomb construct: a Hexa Shield wall, a Hexa Launch platform or one wall of a Hex
 * Prison. The entity carries the gameplay (its own durability, collision, blocking) and a handful
 * of numbers; every individual cell is derived from those numbers by the client renderer.
 */
public class HexConstructEntity extends Entity {
    public static final int KIND_SHIELD = 0;
    public static final int KIND_PLATFORM = 1;
    public static final int KIND_PRISON = 2;

    public static final int ST_ACTIVE = 0;
    /** Platform only: chasing the launched caster upwards. */
    public static final int ST_RISING = 1;
    /** Hexa Spike wind-up: spikes grow while the wall turns to face the caster's aim. */
    public static final int ST_SPIKING = 2;
    public static final int ST_FLYING = 3;
    /** Destroyed: the cells burst apart for a moment before the entity is removed. */
    public static final int ST_BREAKING = 4;
    /** Expired: the cells fade out quietly. */
    public static final int ST_DISSOLVING = 5;

    public static final int BREAK_TICKS = 24;
    public static final int DISSOLVE_TICKS = 16;
    public static final int SPIKE_WINDUP_TICKS = 13;
    public static final int LAUNCH_DELAY_TICKS = 3;
    public static final double SPIKE_SPEED = 1.25;
    public static final double SPIKE_RANGE = 30.0;
    public static final float PLATFORM_THICKNESS = 0.25f;

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HALF_W = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HALF_H = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_HP = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FORM_TICKS = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.FLOAT);
    /** Temporary passage for the caster's own drill: local (u, v) and radius; radius 0 = closed. */
    private static final EntityDataAccessor<Vector3f> APERTURE = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.VECTOR3);
    /** Local (u, v) the destruction spreads from; z = burst direction along the normal. */
    private static final EntityDataAccessor<Vector3f> BREAK_ORIGIN = SynchedEntityData.defineId(HexConstructEntity.class, EntityDataSerializers.VECTOR3);

    // ---- server state ----
    @Nullable
    private UUID ownerUuid;
    private int maxLife = 600;
    private int apertureHold;
    private double flown;
    private double lastOwnerY = Double.NaN;
    private int stallTicks;
    private long lastAbsorbTick = -1;
    @Nullable
    private Vec3 lastAbsorbPos;
    private final IntOpenHashSet spikeHits = new IntOpenHashSet();
    private boolean countsAsProtectiveField;

    // ---- shared / client animation state ----
    /** Ticks since the last state change. */
    public int stateTicks;
    public float renderYaw, renderYawO, renderPitch, renderPitchO, renderHalfW, renderHalfWO;
    public float apertureOpen, apertureOpenO;
    public float apertureU, apertureV, apertureR;
    public static final int MAX_HITS = 4;
    public final float[] hitU = new float[MAX_HITS];
    public final float[] hitV = new float[MAX_HITS];
    public final float[] hitStrength = new float[MAX_HITS];
    public final int[] hitTick = new int[MAX_HITS];
    private int hitCursor;
    private boolean clientInit;

    public HexConstructEntity(EntityType<? extends HexConstructEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        java.util.Arrays.fill(hitTick, -1000);
    }

    public static HexConstructEntity create(ServerLevel level, int kind, @Nullable LivingEntity owner, Vec3 center, float yaw, float pitch,
                                            float halfW, float halfH, int formTicks, int maxLife) {
        HexConstructEntity e = new HexConstructEntity(HexEntities.CONSTRUCT.get(), level);
        e.entityData.set(KIND, (byte) kind);
        e.entityData.set(YAW, yaw);
        e.entityData.set(PITCH, pitch);
        e.entityData.set(HALF_W, halfW);
        e.entityData.set(HALF_H, halfH);
        float hp = HexConfig.CONSTRUCT_HP.get().floatValue();
        e.entityData.set(HP, hp);
        e.entityData.set(MAX_HP, hp);
        e.entityData.set(SEED, level.random.nextInt());
        e.entityData.set(FORM_TICKS, Math.max(1, formTicks));
        if (owner != null) {
            e.ownerUuid = owner.getUUID();
            e.entityData.set(OWNER, owner.getId());
        }
        e.maxLife = maxLife;
        e.countsAsProtectiveField = kind == KIND_SHIELD || kind == KIND_PLATFORM;
        e.setPos(center.x, center.y, center.z);
        e.refreshBox();
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, (byte) KIND_SHIELD);
        builder.define(STATE, (byte) ST_ACTIVE);
        builder.define(YAW, 0f);
        builder.define(PITCH, 0f);
        builder.define(HALF_W, 1f);
        builder.define(HALF_H, 1f);
        builder.define(HP, 20f);
        builder.define(MAX_HP, 20f);
        builder.define(OWNER, -1);
        builder.define(SEED, 0);
        builder.define(FORM_TICKS, 10);
        builder.define(TARGET_Y, 0f);
        builder.define(APERTURE, new Vector3f());
        builder.define(BREAK_ORIGIN, new Vector3f());
    }

    // ------------------------------------------------------------------ accessors

    public int getKind() {
        return entityData.get(KIND);
    }

    public int getState() {
        return entityData.get(STATE);
    }

    public void setState(int state) {
        if (getState() != state) {
            entityData.set(STATE, (byte) state);
            stateTicks = 0;
        }
    }

    public float getPanelYaw() {
        return entityData.get(YAW);
    }

    public float getPanelPitch() {
        return entityData.get(PITCH);
    }

    public void setPanelRotation(float yaw, float pitch) {
        entityData.set(YAW, yaw);
        entityData.set(PITCH, pitch);
        refreshBox();
    }

    public float getHalfW() {
        return entityData.get(HALF_W);
    }

    public float getHalfH() {
        return entityData.get(HALF_H);
    }

    public void setHalfW(float halfW) {
        entityData.set(HALF_W, halfW);
        refreshBox();
    }

    public float getHp() {
        return entityData.get(HP);
    }

    public float getMaxHp() {
        return entityData.get(MAX_HP);
    }

    public int getSeed() {
        return entityData.get(SEED);
    }

    public int getFormTicks() {
        return entityData.get(FORM_TICKS);
    }

    public float getTargetY() {
        return entityData.get(TARGET_Y);
    }

    public Vector3f getBreakOrigin() {
        return entityData.get(BREAK_ORIGIN);
    }

    public int getOwnerId() {
        return entityData.get(OWNER);
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
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

    public boolean isOwnedBy(@Nullable Entity entity) {
        if (entity == null) return false;
        if (ownerUuid != null) return ownerUuid.equals(entity.getUUID());
        return entity.getId() == entityData.get(OWNER);
    }

    /** True for the owner and anything on the owner's side; such entities ignore the barrier. */
    public boolean isFriendly(@Nullable Entity entity) {
        if (entity == null) return false;
        if (isOwnedBy(entity)) return true;
        Entity owner = getOwner();
        return owner != null && HexTargeting.areAllied(owner, entity);
    }

    public static final HexBasis PLATFORM_BASIS = new HexBasis(new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1));

    public HexBasis basis() {
        return getKind() == KIND_PLATFORM ? PLATFORM_BASIS : HexBasis.of(getPanelYaw(), getPanelPitch());
    }

    /** A construct in one of these states still exists physically. */
    public boolean isSolid() {
        int s = getState();
        return s == ST_ACTIVE || s == ST_RISING;
    }

    public boolean isWall() {
        return getKind() != KIND_PLATFORM;
    }

    public boolean isBroken() {
        int s = getState();
        return s == ST_BREAKING || s == ST_DISSOLVING || isRemoved();
    }

    // ------------------------------------------------------------------ geometry

    @Override
    protected AABB makeBoundingBox() {
        Vec3 p = position();
        float hw = getHalfW(), hh = getHalfH();
        if (getKind() == KIND_PLATFORM) {
            return new AABB(p.x - hw, p.y - PLATFORM_THICKNESS, p.z - hh, p.x + hw, p.y, p.z + hh);
        }
        HexBasis b = basis();
        double ex = Math.abs(b.u().x) * hw + Math.abs(b.v().x) * hh + Math.abs(b.f().x) * 0.15;
        double ey = Math.abs(b.u().y) * hw + Math.abs(b.v().y) * hh + Math.abs(b.f().y) * 0.15;
        double ez = Math.abs(b.u().z) * hw + Math.abs(b.v().z) * hh + Math.abs(b.f().z) * 0.15;
        return new AABB(p.x - ex, p.y - ey, p.z - ez, p.x + ex, p.y + ey, p.z + ez);
    }

    private void refreshBox() {
        setBoundingBox(makeBoundingBox());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3.0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key) || YAW.equals(key) || PITCH.equals(key) || HALF_W.equals(key) || HALF_H.equals(key)) {
            refreshBox();
        }
        if (level().isClientSide) {
            if (STATE.equals(key)) stateTicks = 0;
            if (APERTURE.equals(key)) {
                Vector3f a = entityData.get(APERTURE);
                if (a.z > 0f) {
                    apertureU = a.x;
                    apertureV = a.y;
                    apertureR = a.z;
                }
            }
        }
    }

    /**
     * Where the segment a->b crosses this panel, in local coordinates (u, v, t) with t the fraction
     * along the segment, or null when it misses. {@code margin} widens the panel.
     */
    @Nullable
    public Vector3f intersect(Vec3 a, Vec3 b, double margin) {
        HexBasis basis = basis();
        Vec3 c = panelCenter();
        Vec3 n = basis.f();
        double da = (a.x - c.x) * n.x + (a.y - c.y) * n.y + (a.z - c.z) * n.z;
        double db = (b.x - c.x) * n.x + (b.y - c.y) * n.y + (b.z - c.z) * n.z;
        if (da == db || (da > 0) == (db > 0)) return null;
        double t = da / (da - db);
        double px = a.x + (b.x - a.x) * t - c.x, py = a.y + (b.y - a.y) * t - c.y, pz = a.z + (b.z - a.z) * t - c.z;
        double lu = px * basis.u().x + py * basis.u().y + pz * basis.u().z;
        double lv = px * basis.v().x + py * basis.v().y + pz * basis.v().z;
        if (Math.abs(lu) > getHalfW() + margin || Math.abs(lv) > getHalfH() + margin) return null;
        return new Vector3f((float) lu, (float) lv, (float) t);
    }

    /** Centre of the honeycomb plane. For platforms this is the top surface. */
    public Vec3 panelCenter() {
        return position();
    }

    public Vec3 localToWorld(float lu, float lv) {
        return basis().toWorld(panelCenter(), lu, lv, 0);
    }

    /**
     * Displacement that moves {@code e} out of this wall, or null when it does not touch it.
     * Shared by the server (mobs) and the client (the local player predicts its own collision).
     */
    @Nullable
    public Vec3 computePush(Entity e) {
        if (!isWall() || getState() != ST_ACTIVE) return null;
        HexBasis b = basis();
        Vec3 c = panelCenter();
        AABB bb = e.getBoundingBox();
        double cx = (bb.minX + bb.maxX) * 0.5 - c.x, cy = (bb.minY + bb.maxY) * 0.5 - c.y, cz = (bb.minZ + bb.maxZ) * 0.5 - c.z;
        double r = bb.getXsize() * 0.5 + 0.1;
        double lu = cx * b.u().x + cy * b.u().y + cz * b.u().z;
        if (Math.abs(lu) > getHalfW() + r * 0.5) return null;
        double lv = cx * b.v().x + cy * b.v().y + cz * b.v().z;
        if (Math.abs(lv) > getHalfH() + bb.getYsize() * 0.5) return null;
        double d = cx * b.f().x + cy * b.f().y + cz * b.f().z;
        // Where the entity stood a tick ago decides which side it belongs on, so a fast mover that
        // stepped across in one tick is put back instead of being helped through.
        double mx = e.getX() - e.xo, my = e.getY() - e.yo, mz = e.getZ() - e.zo;
        double moved = mx * b.f().x + my * b.f().y + mz * b.f().z;
        double prevD = d - moved;
        boolean crossed = prevD != 0 && (prevD > 0) != (d > 0) && Math.abs(moved) < 1.2;
        if (!crossed && Math.abs(d) >= r) return null;
        double side;
        if (crossed) side = prevD > 0 ? 1 : -1;
        else if (d != 0) side = d > 0 ? 1 : -1;
        else side = moved > 0 ? -1 : 1;
        double push = side * r - d;
        return new Vec3(b.f().x * push, 0, b.f().z * push);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        stateTicks++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (apertureHold > 0 && --apertureHold == 0) {
            entityData.set(APERTURE, new Vector3f());
        }
        switch (getState()) {
            case ST_ACTIVE -> {
                if (tickCount >= maxLife) {
                    dissolve();
                    return;
                }
                absorbProjectiles(level);
                if (isWall()) pushEntities(level);
            }
            case ST_RISING -> tickRising(level);
            case ST_SPIKING -> tickSpiking(level);
            case ST_FLYING -> tickFlying(level);
            case ST_BREAKING -> {
                if (stateTicks >= BREAK_TICKS) discard();
            }
            case ST_DISSOLVING -> {
                if (stateTicks >= DISSOLVE_TICKS) discard();
            }
            default -> discard();
        }
    }

    private void clientTick() {
        float yaw = getPanelYaw(), pitch = getPanelPitch(), hw = getHalfW();
        if (!clientInit) {
            clientInit = true;
            renderYaw = renderYawO = yaw;
            renderPitch = renderPitchO = pitch;
            renderHalfW = renderHalfWO = hw;
        }
        renderYawO = renderYaw;
        renderPitchO = renderPitch;
        renderHalfWO = renderHalfW;
        renderYaw += Mth.wrapDegrees(yaw - renderYaw) * 0.6f;
        renderPitch += (pitch - renderPitch) * 0.6f;
        renderHalfW += (hw - renderHalfW) * 0.7f;
        apertureOpenO = apertureOpen;
        boolean open = entityData.get(APERTURE).z > 0f;
        apertureOpen += ((open ? 1f : 0f) - apertureOpen) * (open ? 0.55f : 0.3f);
        if (apertureOpen < 0.004f && !open) apertureOpen = 0f;
    }

    /** Client: remember a hit so the renderer can send a ripple across the cells. */
    public void addClientHit(Vec3 world, float strength) {
        HexBasis b = basis();
        Vec3 c = panelCenter();
        double px = world.x - c.x, py = world.y - c.y, pz = world.z - c.z;
        hitU[hitCursor] = (float) (px * b.u().x + py * b.u().y + pz * b.u().z);
        hitV[hitCursor] = (float) (px * b.v().x + py * b.v().y + pz * b.v().z);
        hitStrength[hitCursor] = strength;
        hitTick[hitCursor] = tickCount;
        hitCursor = (hitCursor + 1) % MAX_HITS;
    }

    // ------------------------------------------------------------------ walls

    private void pushEntities(ServerLevel level) {
        boolean ownerPasses = HexConfig.OWNER_PASSES_THROUGH.get();
        List<Entity> list = level.getEntities(this, getBoundingBox().inflate(1.4), e -> !(e instanceof HexConstructEntity)
                && !(e instanceof HexDrillEntity) && !(e instanceof HexSwarmEntity) && !(e instanceof HexPrisonEntity)
                && !(e instanceof Projectile) && !e.isSpectator() && !e.noPhysics && !e.isPassenger() && e.isAlive());
        for (Entity e : list) {
            if (ownerPasses && isFriendly(e)) continue;
            Vec3 push = computePush(e);
            if (push == null) continue;
            if (e instanceof ServerPlayer sp) {
                // Players predict this on their own client; the server only corrects real intrusions.
                if (push.lengthSqr() > 0.2 * 0.2) {
                    sp.teleportTo(sp.getX() + push.x, sp.getY(), sp.getZ() + push.z);
                }
                continue;
            }
            e.setPos(e.getX() + push.x, e.getY(), e.getZ() + push.z);
            Vec3 vel = e.getDeltaMovement();
            double len = push.length();
            if (len > 1.0e-6) {
                double nx = push.x / len, nz = push.z / len;
                double into = vel.x * nx + vel.z * nz;
                if (into < 0) e.setDeltaMovement(vel.x - nx * into, vel.y, vel.z - nz * into);
            }
        }
    }

    private void absorbProjectiles(ServerLevel level) {
        List<Projectile> list = level.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(4.0), Entity::isAlive);
        for (Projectile p : list) {
            Entity shooter = p.getOwner();
            if (shooter != null && isFriendly(shooter)) continue;
            Vec3 a = p.position();
            Vec3 vel = p.getDeltaMovement();
            if (vel.lengthSqr() < 1.0e-4) continue;
            // Check the step it just made as well as the one it is about to make: depending on tick
            // order a fast projectile may already have crossed the plane this tick.
            Vector3f hit = intersect(new Vec3(p.xo, p.yo, p.zo), a, 0.1);
            if (hit == null) hit = intersect(a, a.add(vel), 0.1);
            if (hit == null) continue;
            Vec3 world = localToWorld(hit.x, hit.y);
            float amount = HexCasting.estimateProjectileDamage(p);
            p.discard();
            damage(amount, world, shooter);
            if (isRemoved() || getState() != ST_ACTIVE) return;
        }
    }

    /**
     * Called for damage that travelled from {@code from} to {@code to}. Returns true when this wall
     * stood in the way and took the hit instead.
     */
    public boolean tryAbsorb(Vec3 from, Vec3 to, float amount, @Nullable Entity attacker) {
        if (!isSolid() || (attacker != null && isFriendly(attacker))) return false;
        Vector3f hit = intersect(from, to, 0.05);
        if (hit == null) return false;
        long now = level().getGameTime();
        // One explosion can reach several victims behind the same wall; it only costs durability once.
        if (now == lastAbsorbTick && lastAbsorbPos != null && lastAbsorbPos.distanceToSqr(from) < 0.01) return true;
        lastAbsorbTick = now;
        lastAbsorbPos = from;
        damage(amount, localToWorld(hit.x, hit.y), attacker);
        return true;
    }

    // ------------------------------------------------------------------ durability

    /** Applies damage to this construct only. Returns true when it was destroyed. */
    public boolean damage(float amount, Vec3 worldHit, @Nullable Entity attacker) {
        if (level().isClientSide || isBroken() || amount <= 0f) return false;
        ServerLevel level = (ServerLevel) level();
        float hp = getHp() - amount;
        entityData.set(HP, Math.max(0f, hp));
        float frac = 1f - Mth.clamp(hp / getMaxHp(), 0f, 1f);
        HexFx.send(level, HexFx.WALL_HIT, worldHit, Vec3.ZERO, Mth.clamp(amount / 6f, 0.4f, 2.5f), frac, getId());
        level.playSound(null, worldHit.x, worldHit.y, worldHit.z, com.hexmagic.registry.HexSounds.HEX_HIT.get(), SoundSource.PLAYERS, 1.0f, 1.25f - frac * 0.5f);
        if (hp <= 0f) {
            shatter(worldHit, attacker == null ? 0f : 1f);
            if (countsAsProtectiveField) {
                Entity owner = getOwner();
                if (owner instanceof ServerPlayer sp) HexCasting.onProtectiveFieldDestroyed(sp);
            }
            return true;
        }
        return false;
    }

    /** Destroys the construct with the cell-burst animation, starting at {@code worldOrigin}. */
    public void shatter(Vec3 worldOrigin, float push) {
        if (level().isClientSide || isBroken()) return;
        HexBasis b = basis();
        Vec3 c = panelCenter();
        double px = worldOrigin.x - c.x, py = worldOrigin.y - c.y, pz = worldOrigin.z - c.z;
        entityData.set(BREAK_ORIGIN, new Vector3f(
                (float) (px * b.u().x + py * b.u().y + pz * b.u().z),
                (float) (px * b.v().x + py * b.v().y + pz * b.v().z), push));
        setState(ST_BREAKING);
        level().playSound(null, getX(), getY(), getZ(), com.hexmagic.registry.HexSounds.HEX_SHATTER.get(), SoundSource.PLAYERS, 1.4f, 0.9f + level().random.nextFloat() * 0.2f);
    }

    public void dissolve() {
        if (level().isClientSide || isBroken()) return;
        setState(ST_DISSOLVING);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isBroken()) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            discard();
            return true;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && isFriendly(attacker)) return false;
        if (HexDamage.isHexDamage(source)) return false;
        Vec3 from = source.getSourcePosition();
        damage(amount, from == null ? panelCenter() : clampToPanel(from), attacker);
        return true;
    }

    private Vec3 clampToPanel(Vec3 world) {
        HexBasis b = basis();
        Vec3 c = panelCenter();
        double px = world.x - c.x, py = world.y - c.y, pz = world.z - c.z;
        double lu = Mth.clamp(px * b.u().x + py * b.u().y + pz * b.u().z, -getHalfW(), getHalfW());
        double lv = Mth.clamp(px * b.v().x + py * b.v().y + pz * b.v().z, -getHalfH(), getHalfH());
        return b.toWorld(c, lu, lv, 0);
    }

    // ------------------------------------------------------------------ own drill passage

    public boolean hasAperture() {
        return entityData.get(APERTURE).z > 0f;
    }

    /** Opens (or keeps open) a hole around the local point for the caster's own attack. */
    public void requestAperture(float lu, float lv, float radius, int holdTicks) {
        Vector3f cur = entityData.get(APERTURE);
        if (cur.z <= 0f || Math.abs(cur.x - lu) > 0.15f || Math.abs(cur.y - lv) > 0.15f || Math.abs(cur.z - radius) > 0.1f) {
            entityData.set(APERTURE, new Vector3f(lu, lv, radius));
        }
        apertureHold = Math.max(apertureHold, holdTicks);
    }

    /**
     * Opens a passage in every barrier of {@code owner} that the segment crosses. Returns true when
     * at least one barrier is in the way.
     */
    public static boolean openAperturesAlong(ServerLevel level, Entity owner, Vec3 from, Vec3 to, float radius, int holdTicks) {
        boolean any = false;
        for (HexConstructEntity c : level.getEntitiesOfClass(HexConstructEntity.class, new AABB(from, to).inflate(6.0))) {
            if (!c.isSolid() || !c.isFriendly(owner)) continue;
            Vector3f hit = c.intersect(from, to, radius * 0.5);
            if (hit == null) continue;
            c.requestAperture(hit.x, hit.y, radius, holdTicks);
            any = true;
        }
        return any;
    }

    // ------------------------------------------------------------------ Hexa Launch platform

    public void beginRise(double targetY) {
        entityData.set(TARGET_Y, (float) targetY);
        setState(ST_RISING);
    }

    private void tickRising(ServerLevel level) {
        Entity owner = getOwner();
        if (!(owner instanceof LivingEntity living) || !living.isAlive() || owner.level() != level) {
            setState(ST_ACTIVE);
            return;
        }
        double targetY = getTargetY();
        if (stateTicks == LAUNCH_DELAY_TICKS) {
            double vy = launchVelocity(targetY - owner.getY() + 0.9, living.getGravity());
            Vec3 v = owner.getDeltaMovement();
            owner.setDeltaMovement(v.x * 0.4, vy, v.z * 0.4);
            owner.hurtMarked = true;
            owner.resetFallDistance();
            HexFx.send(level, HexFx.LAUNCH_RING, position(), new Vec3(0, 1, 0), getHalfW() * 1.3f, 1f);
            level.playSound(null, getX(), getY(), getZ(), com.hexmagic.registry.HexSounds.LAUNCH.get(), SoundSource.PLAYERS, 1.3f, 1.0f);
        }
        double oy = owner.getY();
        double climb = Double.isNaN(lastOwnerY) ? 1.0 : oy - lastOwnerY;
        lastOwnerY = oy;
        // Player positions arrive from the client and may skip a tick: only a sustained stop counts.
        stallTicks = climb <= 0.0 ? stallTicks + 1 : 0;
        double y = Math.max(getY(), Math.min(oy - 0.02, targetY));
        boolean reached = y >= targetY - 0.01;
        boolean stalled = stateTicks > LAUNCH_DELAY_TICKS + 4 && stallTicks >= 4;
        if (reached || stalled || stateTicks > 80) {
            double lockY = reached ? targetY : y;
            entityData.set(TARGET_Y, (float) lockY);
            setPos(owner.getX(), lockY, owner.getZ());
            setState(ST_ACTIVE);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.3f);
            return;
        }
        setPos(owner.getX(), y, owner.getZ());
    }

    /** Upward speed that carries a jumping entity {@code height} blocks under vanilla drag. */
    public static double launchVelocity(double height, double gravity) {
        double lo = 0.0, hi = 3.9;
        for (int i = 0; i < 24; i++) {
            double mid = (lo + hi) * 0.5;
            double v = mid, h = 0;
            for (int t = 0; t < 200 && v > 0; t++) {
                h += v;
                v = (v - gravity) * 0.98;
            }
            if (h < height) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    @Override
    public boolean canBeCollidedWith() {
        return getKind() == KIND_PLATFORM && isSolid() && !(getState() == ST_RISING && stateTicks < LAUNCH_DELAY_TICKS);
    }

    // ------------------------------------------------------------------ Hexa Spike

    public boolean canBecomeSpike() {
        return getKind() == KIND_SHIELD && getState() == ST_ACTIVE;
    }

    public void beginSpike() {
        spikeHits.clear();
        setState(ST_SPIKING);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.4f, 0.55f);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    private void tickSpiking(ServerLevel level) {
        Entity owner = getOwner();
        if (owner != null && owner.isAlive()) {
            // Keep following the caster's aim while the spikes grow; the direction locks on launch.
            float yaw = getPanelYaw() + Mth.wrapDegrees(owner.getYRot() - getPanelYaw()) * 0.4f;
            float pitch = getPanelPitch() + (Mth.clamp(owner.getXRot(), -60f, 60f) - getPanelPitch()) * 0.4f;
            setPanelRotation(yaw, pitch);
        }
        if (stateTicks >= SPIKE_WINDUP_TICKS) {
            flown = 0;
            setState(ST_FLYING);
            Vec3 f = basis().f();
            HexFx.send(level, HexFx.LAUNCH_RING, position(), f, Math.max(getHalfW(), getHalfH()) * 1.2f, 0.6f);
            level.playSound(null, getX(), getY(), getZ(), com.hexmagic.registry.HexSounds.DRILL_FIRE.get(), SoundSource.PLAYERS, 1.2f, 1.25f);
        }
    }

    private void tickFlying(ServerLevel level) {
        HexBasis b = basis();
        Vec3 f = b.f();
        Vec3 start = position();
        Vec3 end = start.add(f.scale(SPIKE_SPEED));
        Entity owner = getOwner();
        float hw = getHalfW(), hh = getHalfH();

        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(f.scale(SPIKE_SPEED + 1.2)).inflate(0.6),
                e -> e.isAlive() && !e.isSpectator() && !isFriendly(e) && !spikeHits.contains(e.getId()));
        float dmg = HexConfig.SPIKE_DAMAGE.get().floatValue() * IronsCompat.damageMultiplier(owner);
        double kb = HexConfig.SPIKE_KNOCKBACK.get();
        for (LivingEntity e : victims) {
            AABB bb = e.getBoundingBox();
            double cx = (bb.minX + bb.maxX) * 0.5 - start.x, cy = (bb.minY + bb.maxY) * 0.5 - start.y, cz = (bb.minZ + bb.maxZ) * 0.5 - start.z;
            double d = cx * f.x + cy * f.y + cz * f.z;
            if (d < -0.8 || d > 1.1 + SPIKE_SPEED) continue;
            double lu = cx * b.u().x + cy * b.u().y + cz * b.u().z;
            double lv = cx * b.v().x + cy * b.v().y + cz * b.v().z;
            if (Math.abs(lu) > hw + bb.getXsize() * 0.5 || Math.abs(lv) > hh + bb.getYsize() * 0.5) continue;
            spikeHits.add(e.getId());
            e.invulnerableTime = 0;
            if (e.hurt(HexDamage.source(level, HexDamage.SPIKE, this, owner), dmg)) {
                e.push(f.x * kb, 0.22 + Math.max(0, f.y) * kb, f.z * kb);
                e.hurtMarked = true;
            }
            Vec3 at = new Vec3((bb.minX + bb.maxX) * 0.5, (bb.minY + bb.maxY) * 0.5, (bb.minZ + bb.maxZ) * 0.5);
            HexFx.send(level, HexFx.IMPACT, at, f, 1.1f, 0.5f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.1f, 0.8f);
        }

        // An enemy barrier in the way takes the hit and stops the wall.
        for (HexConstructEntity other : level.getEntitiesOfClass(HexConstructEntity.class, getBoundingBox().expandTowards(f.scale(SPIKE_SPEED)).inflate(1.0))) {
            if (other == this || !other.isSolid() || (owner != null && other.isFriendly(owner))) continue;
            Vector3f hit = other.intersect(start, end.add(f.scale(0.9)), Math.max(hw, hh));
            if (hit == null) continue;
            other.damage(dmg, other.localToWorld(hit.x, hit.y), owner);
            setPos(start.x, start.y, start.z);
            impact(level, start.add(f.scale(0.6)), f);
            return;
        }

        int blocked = 0;
        boolean centerBlocked = false;
        double[][] samples = {{0, 0}, {0.6, 0}, {-0.6, 0}, {0, 0.6}, {0, -0.6}};
        for (int i = 0; i < samples.length; i++) {
            Vec3 s = b.toWorld(end, samples[i][0] * hw, samples[i][1] * hh, 0.9);
            BlockPos pos = BlockPos.containing(s);
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                blocked++;
                if (i == 0) centerBlocked = true;
            }
        }
        if (centerBlocked || blocked >= 2) {
            impact(level, end.add(f.scale(0.6)), f);
            return;
        }

        setPos(end.x, end.y, end.z);
        flown += SPIKE_SPEED;
        if (flown >= SPIKE_RANGE) dissolve();
    }

    private void impact(ServerLevel level, Vec3 at, Vec3 dir) {
        HexFx.send(level, HexFx.IMPACT, at, dir, 2.2f, 0.35f);
        HexFx.send(level, HexFx.GUST, at, dir, 2.4f, 1f);
        shatter(at, 1f);
    }

    // ------------------------------------------------------------------ entity plumbing

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
    public boolean ignoreExplosion(Explosion explosion) {
        return isBroken();
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
