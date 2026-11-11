package com.hexmagic.entity;

import com.hexmagic.HexConfig;
import com.hexmagic.compat.IronsCompat;
import com.hexmagic.net.HexFx;
import com.hexmagic.registry.HexDamage;
import com.hexmagic.registry.HexEntities;
import com.hexmagic.util.HexBasis;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hex Prison: the invisible conductor of four independent honeycomb walls. Each wall is its own
 * {@link HexConstructEntity} with its own durability; this entity only runs the timeline - form,
 * hold, close in, crush.
 */
public class HexPrisonEntity extends Entity {
    /** Ticks the caster's two preparation walls need to swing shut before the room appears. */
    public static final int PREP_TICKS = 9;
    public static final float HALF_LONG = 2.0f;
    public static final float HALF_SHORT = 1.5f;
    public static final float HEIGHT = 4.0f;
    public static final float MIN_GAP = 0.42f;

    @Nullable
    private UUID ownerUuid;
    private int ownerId = -1;
    private int targetId = -1;
    private float yaw;
    private Vec3 center = Vec3.ZERO;
    private final HexConstructEntity[] walls = new HexConstructEntity[4];
    private boolean built;
    private int age;
    private int lastSoundStage = -1;

    public HexPrisonEntity(EntityType<? extends HexPrisonEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HexPrisonEntity create(ServerLevel level, LivingEntity owner, LivingEntity target) {
        HexPrisonEntity e = new HexPrisonEntity(HexEntities.PRISON.get(), level);
        e.ownerUuid = owner.getUUID();
        e.ownerId = owner.getId();
        e.targetId = target.getId();
        Vec3 to = target.position().subtract(owner.position());
        e.yaw = to.horizontalDistanceSqr() < 1.0e-4 ? owner.getYRot() : HexBasis.yawOf(to.x, to.z);
        e.setPos(target.getX(), target.getY(), target.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    public boolean isOwnedBy(Entity entity) {
        return ownerUuid != null && ownerUuid.equals(entity.getUUID());
    }

    /** Whether {@code entity} currently stands inside the room. */
    public boolean contains(Entity entity) {
        if (!built) return false;
        return roomBox(1.0f).intersects(entity.getBoundingBox());
    }

    private AABB roomBox(float closedFraction) {
        HexBasis b = HexBasis.of(yaw, 0f);
        double side = Mth.lerp(closedFraction, MIN_GAP + 0.5, HALF_LONG);
        double depth = Mth.lerp(closedFraction, MIN_GAP + 0.5, HALF_SHORT);
        double ex = Math.abs(b.u().x) * side + Math.abs(b.f().x) * depth;
        double ez = Math.abs(b.u().z) * side + Math.abs(b.f().z) * depth;
        return new AABB(center.x - ex, center.y - 0.5, center.z - ez, center.x + ex, center.y + HEIGHT, center.z + ez);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        age++;
        Entity owner = ownerUuid == null ? null : level.getEntity(ownerUuid);

        if (!built) {
            if (age < PREP_TICKS) return;
            Entity target = level.getEntity(targetId);
            if (!(target instanceof LivingEntity living) || !living.isAlive() || !(owner instanceof LivingEntity livingOwner)) {
                discard();
                return;
            }
            build(level, livingOwner, living);
            return;
        }

        int t = age - PREP_TICKS;
        int duration = HexConfig.PRISON_DURATION_TICKS.get();
        int closeStart = Math.min(HexConfig.PRISON_CLOSE_START_TICKS.get(), duration - 10);
        int alive = 0;
        for (HexConstructEntity w : walls) if (w != null && !w.isBroken()) alive++;
        if (alive == 0) {
            discard();
            return;
        }

        if (t >= closeStart) {
            float raw = Mth.clamp((t - closeStart) / (float) (duration - closeStart), 0f, 1f);
            // Slow, menacing start that accelerates into the final snap.
            float s = raw * raw * (0.55f + 0.45f * raw);
            layout(s);
            int stage = (int) (raw * 6f);
            if (stage != lastSoundStage) {
                lastSoundStage = stage;
                level.playSound(null, center.x, center.y + 1.5, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.3f, 0.5f + raw * 0.9f);
                level.playSound(null, center.x, center.y + 1.5, center.z, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.6f, 0.5f + raw * 0.5f);
            }
        }

        if (t >= duration) {
            crush(level, owner, alive);
            discard();
        }
    }

    private void build(ServerLevel level, LivingEntity owner, LivingEntity target) {
        built = true;
        center = target.position();
        setPos(center.x, center.y, center.z);
        int life = HexConfig.PRISON_DURATION_TICKS.get() + 60;
        for (int i = 0; i < 4; i++) {
            walls[i] = HexConstructEntity.create(level, HexConstructEntity.KIND_PRISON, owner, wallCenter(i, 0f), wallYaw(i), 0f,
                    wallHalfWidth(i, 0f), HEIGHT * 0.5f + 0.1f, 12, life);
            level.addFreshEntity(walls[i]);
        }
        level.playSound(null, center.x, center.y + 1.5, center.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.6f, 0.5f);
        level.playSound(null, center.x, center.y + 1.5, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
        level.playSound(null, center.x, center.y + 1.5, center.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    /** Walls 0/1 close the room along the caster's line of sight, walls 2/3 are the sides. */
    private Vec3 wallOutward(int i) {
        HexBasis b = HexBasis.of(yaw, 0f);
        return switch (i) {
            case 0 -> b.f();
            case 1 -> b.f().scale(-1);
            case 2 -> b.u();
            default -> b.u().scale(-1);
        };
    }

    private float wallYaw(int i) {
        Vec3 o = wallOutward(i);
        return HexBasis.yawOf(o.x, o.z);
    }

    private float offset(int i, float s) {
        return Mth.lerp(s, i < 2 ? HALF_SHORT : HALF_LONG, MIN_GAP);
    }

    /** A wall is exactly as wide as the gap between its two neighbours, so the corners stay joined. */
    private float wallHalfWidth(int i, float s) {
        return Mth.lerp(s, i < 2 ? HALF_LONG : HALF_SHORT, MIN_GAP) + 0.12f;
    }

    private Vec3 wallCenter(int i, float s) {
        Vec3 o = wallOutward(i);
        float d = offset(i, s);
        return new Vec3(center.x + o.x * d, center.y + HEIGHT * 0.5 - 0.1, center.z + o.z * d);
    }

    private void layout(float s) {
        for (int i = 0; i < 4; i++) {
            HexConstructEntity w = walls[i];
            if (w == null || w.isBroken()) continue;
            Vec3 c = wallCenter(i, s);
            w.setPos(c.x, c.y, c.z);
            w.setHalfW(wallHalfWidth(i, s));
        }
    }

    private void crush(ServerLevel level, @Nullable Entity owner, int alive) {
        Vec3 mid = center.add(0, HEIGHT * 0.4, 0);
        // With a wall broken open there is nothing left to press against: the trap simply fails.
        if (alive >= 3) {
            float dmg = HexConfig.PRISON_DAMAGE.get().floatValue() * IronsCompat.damageMultiplier(owner);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, roomBox(0f), LivingEntity::isAlive)) {
                if (owner != null && e == owner) continue;
                e.invulnerableTime = 0;
                e.hurt(HexDamage.source(level, HexDamage.CRUSH, this, owner), dmg);
            }
            HexFx.send(level, HexFx.CRUSH, mid, new Vec3(0, 1, 0), 2.4f, 1f);
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2f, 0.8f);
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.5f);
            for (HexConstructEntity w : walls) {
                if (w != null && !w.isBroken()) w.shatter(mid, 1f);
            }
        } else {
            for (HexConstructEntity w : walls) {
                if (w != null && !w.isBroken()) w.dissolve();
            }
        }
    }

    /** Ends the prison early (owner left, died, changed dimension). */
    public void release() {
        for (HexConstructEntity w : walls) {
            if (w != null && !w.isBroken()) w.dissolve();
        }
        discard();
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
