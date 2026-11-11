package com.hexmagic.client.render;

import com.hexmagic.entity.HexDrillEntity;
import com.hexmagic.entity.HexSwarmEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The ring of small drills circling above the caster's head. */
public class HexSwarmRenderer extends EntityRenderer<HexSwarmEntity> {
    private final Vector3f tip = new Vector3f(), dir = new Vector3f(), tmp = new Vector3f();

    public HexSwarmRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(HexSwarmEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity ownerRaw = e.level().getEntity(e.getOwnerIdClient());
        if (!(ownerRaw instanceof LivingEntity owner)) return;
        int state = e.getState();
        float age = e.tickCount + partial;
        float stateAge = Math.max(0f, e.stateTicks - 1 + partial);
        double time = e.level().getGameTime() + partial;
        int count = e.getCount();
        float aim = Mth.lerp(partial, e.aimO, e.aim);
        float formed = state == HexSwarmEntity.ST_FORMING ? Mth.clamp(stateAge / HexSwarmEntity.FORM_TICKS, 0f, 1f) : 1f;
        float dissolve = state == HexSwarmEntity.ST_DISSOLVING ? Mth.clamp(stateAge / HexSwarmEntity.DISSOLVE_TICKS, 0f, 1f) : 0f;

        // The entity sits at the owner's feet; everything is drawn relative to the owner's render position.
        double ox = Mth.lerp(partial, owner.xo, owner.getX()), oy = Mth.lerp(partial, owner.yo, owner.getY()), oz = Mth.lerp(partial, owner.zo, owner.getZ());
        double ex = Mth.lerp(partial, e.xo, e.getX()), ey = Mth.lerp(partial, e.yo, e.getY()), ez = Mth.lerp(partial, e.zo, e.getZ());
        Vec3 feet = new Vec3(ox, oy, oz);
        Entity target = state == HexSwarmEntity.ST_AIMING ? e.level().getEntity(e.getTargetId()) : null;
        Vec3 aimAt = target != null ? target.getBoundingBox().getCenter() : null;

        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(HexRenderTypes.GLOW);
        VertexConsumer fill = buffers.getBuffer(HexRenderTypes.FILL);
        for (int i = 0; i < count; i++) {
            if (e.isFired(i)) continue;
            Vec3 slot = HexSwarmEntity.slotPos(feet, owner.getBbHeight(), i, count, time);
            Vec3 heading = HexSwarmEntity.slotHeading(i, count, time);
            if (aimAt != null && aim > 0.001f) {
                Vec3 want = aimAt.subtract(slot).normalize();
                heading = heading.scale(1 - aim).add(want.scale(aim)).normalize();
            }
            float f = Mth.clamp((formed * 1.3f - i / (float) count * 0.6f) / 0.5f, 0f, 1f);
            if (f <= 0f) continue;
            float scale = HexDrillEntity.MINI_SCALE * (1f - dissolve * 0.5f);
            float spin = (float) (time * 1.9 + i * 1.3);
            float bright = 1f + aim * 0.5f + (state == HexSwarmEntity.ST_AIMING ? 0.3f * Mth.sin(age * 1.5f + i) : 0f);
            tip.set((float) (slot.x - ex), (float) (slot.y - ey), (float) (slot.z - ez));
            dir.set((float) heading.x, (float) heading.y, (float) heading.z);
            // Tip leads the position so the drill looks like it is flying around the ring.
            tmp.set(dir).mul(HexDrillEntity.lengthFor(scale) * 0.5f);
            tip.add(tmp);
            HexDrillMesh.draw(m, glow, fill, tip, dir, scale, spin, f, dissolve, 0.5f + 0.5f * aim, 0.15f + aim * 0.3f, bright, 1f - dissolve, e.getId() * 17 + i * 101, age);
        }
        super.render(e, entityYaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HexSwarmEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
