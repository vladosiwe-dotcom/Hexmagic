package com.hexmagic.client.render;

import com.hexmagic.entity.HexDrillEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class HexDrillRenderer extends EntityRenderer<HexDrillEntity> {
    private final Vector3f dir = new Vector3f(), tip = new Vector3f();

    public HexDrillRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(HexDrillEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        int state = e.getState();
        float age = e.tickCount + partial;
        float stateAge = Math.max(0f, e.stateTicks - 1 + partial);
        dir.set(Mth.lerp(partial, e.dirXO, e.dirX), Mth.lerp(partial, e.dirYO, e.dirY), Mth.lerp(partial, e.dirZO, e.dirZ));
        if (dir.lengthSquared() < 1.0e-6f) dir.set(0, 0, 1);
        dir.normalize();
        float charge = Mth.lerp(partial, e.clientChargeO, e.clientCharge);
        boolean mini = e.isMini();
        float scale = HexDrillEntity.scaleFor(charge, mini);
        float spin = Mth.lerp(partial, e.spinO, e.spin);
        float formed = state == HexDrillEntity.ST_CHARGING ? Mth.clamp(0.25f + charge * 1.1f + stateAge / 12f, 0f, 1f) : 1f;
        float breakAge = state == HexDrillEntity.ST_BREAKING ? Mth.clamp(stateAge / HexDrillEntity.BREAK_TICKS, 0f, 1f) : 0f;
        float power = mini ? 0.5f : charge;
        float flow = switch (state) {
            case HexDrillEntity.ST_FLYING -> Math.min(1f, stateAge / 4f);
            case HexDrillEntity.ST_GRINDING -> 0.35f;
            default -> 0f;
        };
        float bright = 1f + (state == HexDrillEntity.ST_GRINDING ? 0.4f : 0f) + (state == HexDrillEntity.ST_FLYING ? 0.25f : 0f);
        tip.set(0, 0, 0);
        if (state == HexDrillEntity.ST_GRINDING) {
            // Shake while biting into the target.
            float s = 0.05f * scale;
            tip.set(Mth.sin(age * 9.1f) * s, Mth.cos(age * 7.3f) * s, Mth.sin(age * 11.7f + 1f) * s);
        }
        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(HexRenderTypes.GLOW);
        VertexConsumer fill = buffers.getBuffer(HexRenderTypes.FILL);
        HexDrillMesh.draw(m, glow, fill, tip, dir, scale, spin, formed, breakAge, power, flow, bright, 1f, e.getId() * 31 + 7, age);
        super.render(e, entityYaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HexDrillEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
