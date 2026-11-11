package com.hexmagic.entity;

import com.hexmagic.HexConfig;
import com.hexmagic.ability.HexCasting;
import com.hexmagic.ability.HexTargeting;
import com.hexmagic.compat.IronsCompat;
import com.hexmagic.net.HexFx;
import com.hexmagic.registry.HexDamage;
import com.hexmagic.registry.HexEntities;
import com.hexmagic.registry.HexItems;
import com.hexmagic.registry.HexTags;
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
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3f;

/**
 * The Hexa Drill: a barrier rolled into a spinning cone. It is charged above the caster's head,
 * then flies in a straight line, boring through blocks and grinding into whatever it touches.
 * The small homing drills of Hex Attack are the same entity in "mini" mode.
 *
 * <p>The entity position is the tip of the cone.
 */
public class HexDrillEntity extends Entity {
    public static final int ST_CHARGING = 0;
    public static final int ST_FLYING = 1;
    public static final int ST_GRINDING = 2;
    public static final int ST_BREAKING = 3;

    public static final int BREAK_TICKS = 14;
    /** Ticks for a full charge (3 seconds). */
    public static final float FULL_CHARGE_TICKS = 60f;
    /** Charge thresholds in seconds and the damage each one unlocks. */
    public static final float[] TIER_SECONDS = {0.5f, 1.0f, 1.5f, 2.0f, 3.0f};
    public static final float[] TIER_DAMAGE = {4f, 6f, 8f, 10f, 12f};
    public static final float GUST_RADIUS = 3.2f;
    public static final float MINI_SCALE = 0.3f;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(HexDrillEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(HexDrillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MINI = SynchedEntityData.defineId(HexDrillEntity.class, EntityDataSerializers.BOOLEAN);
    /** Charge reached at launch, 0..1. */
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(HexDrillEntity.class, EntityDataSerializers.FLOAT);
    /** Charge gained per tick while charging; lets the client predict the charge smoothly. */
    private static final EntityDataAccessor<Float> CHARGE_RATE = SynchedEntityData.defineId(HexDrillEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(HexDrillEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(HexDrillEntity.class, EntityDataSerializers.INT);

    // ---- server state ----
    @Nullable
    private UUID ownerUuid;
    private float charge;
    private int tier = -1;
    private float damage;
    private double energy;
    private double flown;
    private int flightTicks;
    private int grindTicks;
    private int grindHits;
    @Nullable
    private HexConstructEntity grindWall;
    private final IntOpenHashSet passed = new IntOpenHashSet();
    private final IntOpenHashSet gusted = new IntOpenHashSet();

    // ---- client animation state ----
    public int stateTicks;
    public float clientCharge, clientChargeO;
    public float spin, spinO, spinSpeed;
    public float dirX, dirY, dirZ, dirXO, dirYO, dirZO;
    private boolean clientInit;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;

    public HexDrillEntity(EntityType<? extends HexDrillEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static HexDrillEntity createCharging(ServerLevel level, LivingEntity owner) {
        HexDrillEntity e = new HexDrillEntity(HexEntities.DRILL.get(), level);
        e.ownerUuid = owner.getUUID();
        e.entityData.set(OWNER, owner.getId());
        e.entityData.set(CHARGE_RATE, 1f / FULL_CHARGE_TICKS);
        Vec3 look = owner.getLookAngle();
        e.entityData.set(DIR, look.toVector3f());
        Vec3 tip = chargeTip(owner.getEyePosition(), look, scaleFor(0f, false));
        e.setPos(tip.x, tip.y, tip.z);
        return e;
    }

    public static HexDrillEntity createMini(ServerLevel level, LivingEntity owner, Vec3 pos, Vec3 dir, @Nullable Entity target) {
        HexDrillEntity e = new HexDrillEntity(HexEntities.DRILL.get(), level);
        e.ownerUuid = owner.getUUID();
        e.entityData.set(OWNER, owner.getId());
        e.entityData.set(MINI, true);
        e.entityData.set(POWER, 0.5f);
        e.entityData.set(DIR, dir.normalize().toVector3f());
        e.entityData.set(TARGET, target == null ? -1 : target.getId());
        e.entityData.set(STATE, (byte) ST_FLYING);
        e.damage = HexConfig.HEX_ATTACK_DAMAGE.get().floatValue();
        e.energy = 1;
        e.setPos(pos.x, pos.y, pos.z);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, (byte) ST_CHARGING);
        builder.define(OWNER, -1);
        builder.define(MINI, false);
        builder.define(POWER, 0f);
        builder.define(CHARGE_RATE, 1f / FULL_CHARGE_TICKS);
        builder.define(DIR, new Vector3f(0, 0, 1));
        builder.define(TARGET, -1);
    }

    // ------------------------------------------------------------------ shared geometry

    /** Visual scale of the drill for a charge of 0..1. */
    public static float scaleFor(float charge, boolean mini) {
        return mini ? MINI_SCALE : 0.42f + 0.58f * Mth.clamp(charge, 0f, 1f);
    }

    public static float lengthFor(float scale) {
        return 3.5f * scale;
    }

    public static float baseRadiusFor(float scale) {
        return 0.82f * scale;
    }

    /** Tip position of a drill that hovers above the head of a caster looking along {@code look}. */
    public static Vec3 chargeTip(Vec3 eye, Vec3 look, float scale) {
        double up = 0.85 + baseRadiusFor(scale) * 0.75;
        double half = lengthFor(scale) * 0.5;
        return new Vec3(eye.x + look.x * (half + 0.2), eye.y + up + look.y * half, eye.z + look.z * (half + 0.2));
    }

    public static int tierFor(float seconds) {
        int t = -1;
        for (int i = 0; i < TIER_SECONDS.length; i++) if (seconds >= TIER_SECONDS[i] - 1.0e-4f) t = i;
        return t;
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

    public boolean isMini() {
        return entityData.get(MINI);
    }

    public float getPower() {
        return entityData.get(POWER);
    }

    public float getChargeRate() {
        return entityData.get(CHARGE_RATE);
    }

    public Vec3 getDir() {
        Vector3f d = entityData.get(DIR);
        return new Vec3(d.x, d.y, d.z);
    }

    public int getTargetId() {
        return entityData.get(TARGET);
    }

    public float getCharge() {
        return charge;
    }

    public float getDamage() {
        return damage;
    }

    public int getTier() {
        return tier;
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

    public float bodyScale() {
        return scaleFor(getState() == ST_CHARGING ? charge : getPower(), isMini());
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
        switch (getState()) {
            case ST_CHARGING -> tickCharging(level);
            case ST_FLYING -> tickFlying(level);
            case ST_GRINDING -> tickGrinding(level);
            case ST_BREAKING -> {
                if (stateTicks >= BREAK_TICKS) discard();
            }
            default -> discard();
        }
    }

    private void clientTick() {
        Vector3f d = entityData.get(DIR);
        if (!clientInit) {
            clientInit = true;
            dirX = dirXO = d.x;
            dirY = dirYO = d.y;
            dirZ = dirZO = d.z;
            if (getState() != ST_CHARGING) clientCharge = clientChargeO = getPower();
        }
        dirXO = dirX;
        dirYO = dirY;
        dirZO = dirZ;
        dirX += (d.x - dirX) * 0.7f;
        dirY += (d.y - dirY) * 0.7f;
        dirZ += (d.z - dirZ) * 0.7f;

        clientChargeO = clientCharge;
        int state = getState();
        if (state == ST_CHARGING) {
            clientCharge = Math.min(1f, clientCharge + getChargeRate());
        } else {
            clientCharge = getPower();
        }
        float targetSpeed;
        if (isMini()) targetSpeed = 1.9f;
        else if (state == ST_CHARGING) targetSpeed = 0.22f + 1.75f * clientCharge * clientCharge;
        else if (state == ST_GRINDING) targetSpeed = 3.1f;
        else if (state == ST_BREAKING) targetSpeed = 0.4f;
        else targetSpeed = 2.7f;
        spinSpeed += (targetSpeed - spinSpeed) * 0.35f;
        spinO = spin;
        spin += spinSpeed;
        if (spin > Mth.TWO_PI * 64f) {
            spin -= Mth.TWO_PI * 64f;
            spinO -= Mth.TWO_PI * 64f;
        }

        if (!isMini() && state == ST_FLYING && stateTicks % 2 == 0) {
            // Rings of displaced air left hanging behind the drill.
            float scale = scaleFor(clientCharge, false);
            com.hexmagic.client.HexClientHooks.localFx(com.hexmagic.net.HexFx.GUST, position().subtract(dirX * lengthFor(scale), dirY * lengthFor(scale), dirZ * lengthFor(scale)),
                    new Vec3(dirX, dirY, dirZ), baseRadiusFor(scale) * 2.4f, 0.55f + 0.45f * clientCharge);
        }

        if (lerpSteps > 0) {
            double x = getX() + (lerpX - getX()) / lerpSteps;
            double y = getY() + (lerpY - getY()) / lerpSteps;
            double z = getZ() + (lerpZ - getZ()) / lerpSteps;
            lerpSteps--;
            setPos(x, y, z);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = Math.max(1, Math.min(steps, 3));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && STATE.equals(key)) stateTicks = 0;
    }

    // ------------------------------------------------------------------ charging

    private void tickCharging(ServerLevel level) {
        Entity owner = getOwner();
        if (!(owner instanceof LivingEntity living) || !living.isAlive() || owner.level() != level || !HexItems.isHoldingStaff(living)) {
            fizzle();
            return;
        }
        boolean boosted = owner instanceof ServerPlayer sp && HexCasting.isOnOwnPlatform(sp);
        float rate = (boosted ? HexConfig.PLATFORM_CAST_BOOST.get().floatValue() : 1f) / FULL_CHARGE_TICKS;
        if (Math.abs(rate - getChargeRate()) > 1.0e-5f) entityData.set(CHARGE_RATE, rate);
        charge = Math.min(1f, charge + rate);

        int newTier = tierFor(charge * FULL_CHARGE_TICKS / 20f);
        if (newTier > tier) {
            tier = newTier;
            level.playSound(null, owner.getX(), owner.getY() + 2.5, owner.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6f, 0.7f + tier * 0.22f);
            if (tier == TIER_SECONDS.length - 1) {
                level.playSound(null, owner.getX(), owner.getY() + 2.5, owner.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.9f, 1.6f);
            }
        }
        if (stateTicks % 6 == 1) {
            // One grain of whirring rotor, replayed higher and louder as the drill spins up.
            level.playSound(null, owner.getX(), owner.getY() + 2.5, owner.getZ(), com.hexmagic.registry.HexSounds.DRILL_CHARGE.get(), SoundSource.PLAYERS, 0.35f + charge * 0.65f, 0.6f + charge * 1.2f);
        }

        Vec3 look = owner.getLookAngle();
        entityData.set(DIR, look.toVector3f());
        float scale = scaleFor(charge, false);
        Vec3 tip = chargeTip(owner.getEyePosition(), look, scale);
        setPos(tip.x, tip.y, tip.z);

        // Own barriers in the line of fire start opening while the drill is still spinning up, so the
        // passage is ready the moment it is released.
        if (stateTicks % 2 == 0 && charge * FULL_CHARGE_TICKS / 20f >= TIER_SECONDS[0] * 0.5f) {
            Vec3 dir = aimDirection(living, tip);
            HexConstructEntity.openAperturesAlong(level, owner, tip, tip.add(dir.scale(28.0)), boreRadius(charge) + 0.45f, 6);
        }
        // Nobody holds a drill forever: it goes off by itself after a long hold at full charge.
        if (stateTicks > 400 && owner instanceof ServerPlayer sp) HexCasting.releaseDrill(sp);
    }

    /** Direction from the hovering tip to whatever the caster's crosshair rests on. */
    public static Vec3 aimDirection(LivingEntity owner, Vec3 tip) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 far = eye.add(look.scale(64.0));
        BlockHitResult block = owner.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 aim = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        LivingEntity target = HexTargeting.pickLiving(owner, 64.0, 0.5);
        if (target != null && target.distanceToSqr(eye) < aim.distanceToSqr(eye)) {
            aim = target.getBoundingBox().getCenter();
        }
        // Up close the offset above the head would turn into a steep dive, so fire parallel instead.
        if (aim.distanceToSqr(eye) < 6.0 * 6.0) return look;
        return aim.subtract(tip).normalize();
    }

    public static float boreRadius(float power) {
        return 0.4f + 0.6f * power;
    }

    /** Released before the first threshold: the half-formed cone just falls apart. */
    public void fizzle() {
        if (getState() == ST_BREAKING) return;
        entityData.set(POWER, charge);
        setState(ST_BREAKING);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** Launches the charged drill. Returns false when the charge was too small to fire. */
    public boolean launch(LivingEntity owner) {
        if (getState() != ST_CHARGING) return false;
        float seconds = charge * FULL_CHARGE_TICKS / 20f;
        int t = tierFor(seconds);
        if (t < 0) {
            fizzle();
            return false;
        }
        ServerLevel level = (ServerLevel) level();
        tier = t;
        damage = TIER_DAMAGE[t];
        energy = 10.0 + 50.0 * charge;
        float scale = scaleFor(charge, false);
        Vec3 tip = chargeTip(owner.getEyePosition(), owner.getLookAngle(), scale);
        Vec3 dir = aimDirection(owner, tip);
        setPos(tip.x, tip.y, tip.z);
        entityData.set(POWER, charge);
        entityData.set(DIR, dir.toVector3f());
        setState(ST_FLYING);
        flown = 0;
        flightTicks = 0;
        HexConstructEntity.openAperturesAlong(level, owner, tip, tip.add(dir.scale(30.0)), boreRadius(charge) + 0.45f, 14);
        HexFx.send(level, HexFx.LAUNCH_RING, tip.subtract(dir.scale(lengthFor(scale))), dir, baseRadiusFor(scale) * 2.2f, 0.4f + charge * 0.6f, getId());
        level.playSound(null, tip.x, tip.y, tip.z, com.hexmagic.registry.HexSounds.DRILL_FIRE.get(), SoundSource.PLAYERS, 1.0f + charge * 1.0f, 1.25f - charge * 0.35f);
        return true;
    }

    // ------------------------------------------------------------------ flight

    private void tickFlying(ServerLevel level) {
        Entity owner = getOwner();
        boolean mini = isMini();
        Vec3 dir = getDir();
        double speed;
        flightTicks++;
        if (mini) {
            speed = Math.min(0.5 + 0.085 * flightTicks, 1.7);
            Entity target = level.getEntity(getTargetId());
            if (target != null && target.isAlive() && target.level() == level) {
                Vec3 want = target.getBoundingBox().getCenter().subtract(position());
                if (want.lengthSqr() > 1.0e-4) {
                    double turn = Mth.clamp(0.05 + 0.04 * flightTicks, 0.0, 0.65);
                    Vec3 nd = dir.scale(1 - turn).add(want.normalize().scale(turn));
                    if (nd.lengthSqr() > 1.0e-6) {
                        dir = nd.normalize();
                        entityData.set(DIR, dir.toVector3f());
                    }
                }
            }
            if (flightTicks > 80) {
                breakApart(level, position(), dir, false);
                return;
            }
        } else {
            speed = HexConfig.DRILL_SPEED.get();
        }

        float power = getPower();
        float radius = mini ? 0.2f : boreRadius(power);
        Vec3 p = position();
        Vec3 end = p.add(dir.scale(speed));
        AABB sweep = new AABB(p, end);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, sweep.inflate(mini ? 1.5 : GUST_RADIUS + 1.0),
                e -> e.isAlive() && !e.isSpectator() && e != owner && !(owner != null && HexTargeting.areAllied(owner, e)));
        List<HexConstructEntity> walls = level.getEntitiesOfClass(HexConstructEntity.class, sweep.inflate(8.0), HexConstructEntity::isSolid);
        if (owner != null && !mini) {
            HexConstructEntity.openAperturesAlong(level, owner, p, p.add(dir.scale(speed * 4.0)), radius + 0.45f, 5);
        } else if (owner != null) {
            HexConstructEntity.openAperturesAlong(level, owner, p, p.add(dir.scale(speed * 3.0)), 0.45f, 4);
        }

        int steps = Math.max(1, Mth.ceil(speed / 0.4));
        double ds = speed / steps;
        double hitRadius = mini ? 0.25 : 0.25 + 0.4 * power;
        for (int i = 0; i < steps; i++) {
            Vec3 q = p.add(dir.scale(ds));

            for (HexConstructEntity wall : walls) {
                if (wall.isBroken() || (owner != null && wall.isFriendly(owner))) continue;
                Vector3f hit = wall.intersect(p, q, radius * 0.5);
                if (hit == null) continue;
                Vec3 at = wall.localToWorld(hit.x, hit.y);
                setPos(at.x, at.y, at.z);
                beginGrind(level, null, wall);
                return;
            }

            for (LivingEntity e : near) {
                if (passed.contains(e.getId())) continue;
                AABB bb = e.getBoundingBox().inflate(hitRadius);
                if (bb.contains(q) || bb.clip(p, q).isPresent()) {
                    Vec3 at = bb.clip(p, q).orElse(q);
                    setPos(at.x, at.y, at.z);
                    beginGrind(level, e, null);
                    return;
                }
            }

            if (!mini && HexConfig.DRILL_BREAKS_BLOCKS.get()) boreBlocks(level, owner, q, dir, radius);
            BlockHitResult block = level.clip(new ClipContext(p, q, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                Vec3 at = block.getLocation();
                setPos(at.x, at.y, at.z);
                breakApart(level, at, dir, true);
                return;
            }
            p = q;
            flown += ds;
        }
        setPos(p.x, p.y, p.z);

        if (!mini) {
            gust(near, p.subtract(dir.scale(speed)), p, dir);
            if (flightTicks % 3 == 0) {
                level.playSound(null, p.x, p.y, p.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35f, 1.9f);
            }
            if (flown >= HexConfig.DRILL_RANGE.get() || energy <= 0) breakApart(level, p, dir, false);
        }
    }

    /** Pushes bystanders away from the drill's path with the air it drags along. */
    private void gust(List<LivingEntity> near, Vec3 a, Vec3 b, Vec3 dir) {
        for (LivingEntity e : near) {
            if (gusted.contains(e.getId()) || passed.contains(e.getId())) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double t = Mth.clamp(c.subtract(a).dot(dir) / Math.max(1.0e-6, b.subtract(a).dot(dir)), 0.0, 1.0);
            Vec3 closest = a.add(b.subtract(a).scale(t));
            Vec3 off = c.subtract(closest);
            double dist = off.length();
            if (dist > GUST_RADIUS) continue;
            gusted.add(e.getId());
            Vec3 radial = dist > 0.05 ? off.scale(1.0 / dist) : new Vec3(-dir.z, 0, dir.x);
            double strength = (1.0 - dist / GUST_RADIUS) * 0.55 + 0.2;
            e.push(radial.x * strength + dir.x * 0.15, Math.max(0.12, radial.y * strength * 0.6), radial.z * strength + dir.z * 0.15);
            e.hurtMarked = true;
            if (level() instanceof ServerLevel sl) {
                HexFx.send(sl, HexFx.GUST, closest, dir, 1.6f, 0.7f);
            }
        }
    }

    private void boreBlocks(ServerLevel level, @Nullable Entity owner, Vec3 at, Vec3 dir, float radius) {
        ServerPlayer player = owner instanceof ServerPlayer sp ? sp : null;
        if (player != null) {
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode == GameType.ADVENTURE || mode == GameType.SPECTATOR) return;
        }
        double maxHardness = 1.2 + (HexConfig.DRILL_MAX_HARDNESS.get() - 1.2) * getPower();
        int r = Mth.ceil(radius);
        BlockPos center = BlockPos.containing(at);
        boolean drops = HexConfig.DRILL_DROPS_BLOCKS.get();
        int noisy = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            double dx = pos.getX() + 0.5 - at.x, dy = pos.getY() + 0.5 - at.y, dz = pos.getZ() + 0.5 - at.z;
            double along = dx * dir.x + dy * dir.y + dz * dir.z;
            double rx = dx - dir.x * along, ry = dy - dir.y * along, rz = dz - dir.z * along;
            if (Math.abs(along) > 0.75 || rx * rx + ry * ry + rz * rz > (radius + 0.3) * (radius + 0.3)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) continue;
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || hardness > maxHardness || state.hasBlockEntity() || state.is(HexTags.DRILL_IMMUNE)) continue;
            double cost = Math.max(0.25, hardness);
            if (energy < cost) continue;
            if (player != null) {
                if (!level.mayInteract(player, pos)) continue;
                if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) continue;
            }
            energy -= cost;
            BlockPos immutable = pos.immutable();
            if (noisy < 2) {
                level.destroyBlock(immutable, drops, owner);
                noisy++;
            } else {
                if (drops) {
                    BlockEntity be = level.getBlockEntity(immutable);
                    Block.dropResources(state, level, immutable, be, owner, ItemStack.EMPTY);
                }
                level.setBlock(immutable, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            }
        }
    }

    // ------------------------------------------------------------------ contact

    private void beginGrind(ServerLevel level, @Nullable LivingEntity target, @Nullable HexConstructEntity wall) {
        grindTicks = 0;
        grindHits = 0;
        grindWall = wall;
        entityData.set(TARGET, target == null ? -1 : target.getId());
        setState(ST_GRINDING);
        Vec3 dir = getDir();
        HexFx.send(level, HexFx.IMPACT, position(), dir, isMini() ? 0.7f : 1.2f + getPower(), 0.85f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.2f, isMini() ? 1.5f : 0.7f);
        tickGrinding(level);
    }

    private void tickGrinding(ServerLevel level) {
        Entity owner = getOwner();
        Vec3 dir = getDir();
        boolean mini = isMini();
        int interval = HexConfig.DRILL_HIT_INTERVAL_TICKS.get();
        float dmg = damage * IronsCompat.damageMultiplier(owner);

        if (grindWall != null) {
            HexConstructEntity wall = grindWall;
            if (wall.isBroken()) {
                resumeFlight(level, 0.6);
                return;
            }
            if (grindTicks % interval == 0) {
                boolean destroyed = wall.damage(dmg, position(), owner);
                grindHits++;
                level.playSound(null, getX(), getY(), getZ(), com.hexmagic.registry.HexSounds.DRILL_GRIND.get(), SoundSource.PLAYERS, 1.0f, 1.3f);
                if (destroyed) {
                    resumeFlight(level, 0.6);
                    return;
                }
                if (mini || grindHits >= 3) {
                    breakApart(level, position(), dir, true);
                    return;
                }
            }
            grindTicks++;
            return;
        }

        Entity raw = level.getEntity(getTargetId());
        if (!(raw instanceof LivingEntity target) || !target.isAlive() || target.level() != level) {
            if (mini) breakApart(level, position(), dir, false);
            else resumeFlight(level, 0.8);
            return;
        }
        // Stay buried in the target: the tip sits just inside its near side.
        Vec3 c = target.getBoundingBox().getCenter();
        double inset = target.getBbWidth() * 0.3;
        setPos(c.x - dir.x * inset, c.y - dir.y * inset, c.z - dir.z * inset);

        if (grindTicks % interval == 0) {
            target.invulnerableTime = 0;
            boolean last = mini || grindHits + 1 >= HexConfig.DRILL_CONTACT_HITS.get();
            boolean hurt = target.hurt(HexDamage.source(level, HexDamage.DRILL, this, owner), dmg);
            grindHits++;
            if (hurt) {
                if (last) {
                    double kb = mini ? 0.25 : 0.5 + getPower() * 0.5;
                    target.push(dir.x * kb, 0.12 + Math.max(0, dir.y) * kb, dir.z * kb);
                    target.hurtMarked = true;
                } else {
                    target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 1.0, 0.2));
                    if (!(target instanceof Player)) target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, interval + 2, 4, false, false, false));
                }
            }
            HexFx.send(level, HexFx.IMPACT, position(), dir, mini ? 0.6f : 0.9f + getPower() * 0.8f, 1f);
            level.playSound(null, getX(), getY(), getZ(), com.hexmagic.registry.HexSounds.DRILL_GRIND.get(), SoundSource.PLAYERS, mini ? 0.6f : 1.1f, mini ? 1.6f : 1.0f);
            if (last) {
                passed.add(target.getId());
                if (mini) breakApart(level, position(), dir, false);
                else resumeFlight(level, 0.5);
                return;
            }
        }
        grindTicks++;
    }

    private void resumeFlight(ServerLevel level, double energyFactor) {
        energy *= energyFactor;
        grindWall = null;
        entityData.set(TARGET, -1);
        if (energy <= 1.0) {
            breakApart(level, position(), getDir(), false);
            return;
        }
        setState(ST_FLYING);
    }

    private void breakApart(ServerLevel level, Vec3 at, Vec3 dir, boolean hard) {
        if (getState() == ST_BREAKING) return;
        setState(ST_BREAKING);
        boolean mini = isMini();
        if (hard) {
            HexFx.send(level, HexFx.IMPACT, at, dir, mini ? 0.8f : 1.6f + getPower() * 1.4f, 0.7f);
            if (!mini) HexFx.send(level, HexFx.GUST, at, dir, 2.0f + getPower() * 1.5f, 1f);
            level.playSound(null, at.x, at.y, at.z, com.hexmagic.registry.HexSounds.HEX_SHATTER.get(), SoundSource.PLAYERS, mini ? 0.5f : 1.2f, mini ? 1.6f : 0.85f);
            if (!mini) level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.9f, 1.2f);
        } else {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, mini ? 0.5f : 1.0f, mini ? 1.6f : 1.0f);
        }
    }

    // ------------------------------------------------------------------ entity plumbing

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(isMini() ? 2.0 : 6.0);
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
