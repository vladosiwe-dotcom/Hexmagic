package com.hexmagic.client.render;

import com.hexmagic.HexMagic;
import com.hexmagic.client.HexEquipAnimations;
import com.hexmagic.util.HexHash;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders the Hex Staff model and, when it has just been drawn, the summoning animation: honeycomb
 * cells fold in from the void along the shaft while the staff itself grows into the hand.
 */
public class HexStaffRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(HexMagic.id("item/hex_staff_model"));
    private static final int CELLS = 22;
    private static final float[] col = new float[3];

    public HexStaffRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(MODEL);
        boolean leftCtx = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean inHand = leftCtx || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        float progress = 1f;
        LivingEntity holder = HexEquipAnimations.renderingEntity;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        if (inHand && holder != null) {
            InteractionHand hand = leftCtx == (holder.getMainArm() == HumanoidArm.LEFT) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            progress = HexEquipAnimations.progress(holder, hand, partial);
        }
        float time = holder != null ? holder.tickCount + partial : (mc.level != null ? mc.level.getGameTime() + partial : 0f);

        pose.pushPose();
        model = model.applyTransform(ctx, pose, leftCtx);
        pose.translate(-0.5f, -0.5f, -0.5f);

        Matrix4f cellMatrix = null;
        if (progress < 1f) {
            // Grow out of the void: the shaft stretches into place while spinning down into the hand.
            float grow = HexMesh.easeOutBack(Mth.clamp(progress / 0.6f, 0f, 1f));
            float settle = HexMesh.easeOutCubic(Mth.clamp((progress - 0.5f) / 0.5f, 0f, 1f));
            pose.translate(0.5f, 0.125f, 0.5f);
            pose.translate(0f, (1f - grow) * 0.7f, 0f);
            pose.mulPose(Axis.YP.rotationDegrees((1f - HexMesh.easeOutCubic(Mth.clamp(progress / 0.7f, 0f, 1f))) * 540f));
            pose.scale(Mth.lerp(settle, Mth.lerp(grow, 0.3f, 1.06f), 1f), Mth.lerp(settle, Mth.lerp(grow, 0.1f, 1.04f), 1f), Mth.lerp(settle, Mth.lerp(grow, 0.3f, 1.06f), 1f));
            pose.translate(-0.5f, -0.125f, -0.5f);
        }
        if (progress >= 0.15f) {
            VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, ItemBlockRenderTypes.getRenderType(stack, true), true, stack.hasFoil());
            mc.getItemRenderer().renderModelLists(model, stack, light, overlay, pose, vc);
        }
        if (inHand) cellMatrix = new Matrix4f(pose.last().pose());
        pose.popPose();

        if (cellMatrix == null) return;
        VertexConsumer glow = buffers.getBuffer(HexRenderTypes.GLOW);
        VertexConsumer fill = buffers.getBuffer(HexRenderTypes.FILL);
        Vector3f c = new Vector3f(), u = new Vector3f(), v = new Vector3f(), tmp = new Vector3f();
        int seed = holder != null ? holder.getId() * 977 : 1;

        if (progress < 1f) {
            // Cells converge from random points onto the shaft, then disperse as a ring at the head.
            for (int i = 0; i < CELLS; i++) {
                float delay = HexHash.unit(seed, i, 0, 1) * 0.45f;
                float t = Mth.clamp((progress - delay) / 0.4f, 0f, 1f);
                if (t <= 0f) continue;
                float ty = HexHash.unit(seed, i, 0, 2) * 1.9f - 1.0f;
                Vector3f start = HexMesh.jitter(HexHash.mix(seed, i, 0, 3), 2.2f).add(0.5f, ty + 0.125f, 0.5f);
                Vector3f end = new Vector3f(0.5f, ty + 0.125f, 0.5f);
                float move = HexMesh.easeOutCubic(t);
                c.set(start).lerp(end, move);
                float fade = 1f - Mth.clamp((t - 0.75f) / 0.25f, 0f, 1f);
                float spin = time * 0.4f + i;
                u.set(Mth.cos(spin), 0, Mth.sin(spin));
                v.set(0, 1, 0);
                HexMesh.heatColor(0f, col);
                HexMesh.cell(cellMatrix, glow, fill, c, u, v, 0.09f * (1f - move * 0.4f), 0.3f, col[0], col[1], col[2], 1.6f, fade * Math.min(1f, t * 2f), 0.25f);
            }
            float burst = Mth.clamp((progress - 0.62f) / 0.38f, 0f, 1f);
            if (burst > 0f) {
                for (int i = 0; i < 8; i++) {
                    float ang = i * Mth.TWO_PI / 8f + time * 0.2f;
                    float r = 0.1f + HexMesh.easeOutCubic(burst) * 0.55f;
                    c.set(0.5f + Mth.cos(ang) * r, 1.26f + burst * 0.2f, 0.5f + Mth.sin(ang) * r);
                    u.set(-Mth.sin(ang), 0, Mth.cos(ang));
                    v.set(0, 1, 0);
                    HexMesh.cell(cellMatrix, glow, fill, c, u, v, 0.07f * (1f - burst * 0.5f), 0.3f, 0.6f, 0.9f, 1f, 1.5f, 1f - burst, 0.2f);
                }
            }
        }

        // Idle: three faint cells drift around the head, so the staff always looks alive.
        float idleAlpha = progress >= 1f ? 0.55f : 0.2f * progress;
        for (int i = 0; i < 3; i++) {
            float ang = time * 0.06f + i * Mth.TWO_PI / 3f;
            float bob = Mth.sin(time * 0.15f + i * 2f) * 0.05f;
            c.set(0.5f + Mth.cos(ang) * 0.42f, 1.24f + bob, 0.5f + Mth.sin(ang) * 0.42f);
            u.set(-Mth.sin(ang), 0, Mth.cos(ang));
            v.set(0, 1, 0);
            HexMesh.heatColor(0f, col);
            HexMesh.cell(cellMatrix, glow, null, c, u, v, 0.055f, 0.3f, col[0], col[1], col[2], 1.2f, idleAlpha, 0f);
        }
    }
}
