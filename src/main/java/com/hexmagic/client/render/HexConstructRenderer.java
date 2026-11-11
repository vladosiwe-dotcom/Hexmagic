package com.hexmagic.client.render;

import com.hexmagic.entity.HexConstructEntity;
import com.hexmagic.util.HexBasis;
import com.hexmagic.util.HexHash;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a flat honeycomb construct cell by cell. Every cell is derived from the entity's few synced
 * numbers plus a deterministic hash, so all players see the same irregular edges, the same cells
 * reddening first and the same shards flying when it breaks.
 */
public class HexConstructRenderer extends EntityRenderer<HexConstructEntity> {
    private static final int HIT_LIFE = 16;

    private final float[] col = new float[3];
    private final Vector3f c = new Vector3f(), cu = new Vector3f(), cv = new Vector3f(), tmp = new Vector3f(), tmp2 = new Vector3f();
    private final Vector3f U = new Vector3f(), V = new Vector3f(), F = new Vector3f();

    public HexConstructRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(HexConstructEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        int kind = e.getKind();
        int state = e.getState();
        float age = e.tickCount + partial;
        float stateAge = Math.max(0f, e.stateTicks - 1 + partial);
        float yaw = Mth.rotLerp(partial, e.renderYawO, e.renderYaw);
        float pitch = Mth.lerp(partial, e.renderPitchO, e.renderPitch);
        float halfW = Mth.lerp(partial, e.renderHalfWO, e.renderHalfW);
        float halfH = e.getHalfH();
        if (halfW <= 0.01f) halfW = e.getHalfW();
        HexBasis basis = kind == HexConstructEntity.KIND_PLATFORM ? HexConstructEntity.PLATFORM_BASIS : HexBasis.of(yaw, pitch);
        set(U, basis.u());
        set(V, basis.v());
        set(F, basis.f());
        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(HexRenderTypes.GLOW);
        VertexConsumer fill = buffers.getBuffer(HexRenderTypes.FILL);

        int seed = e.getSeed();
        int formTicks = e.getFormTicks();
        float damage = 1f - Mth.clamp(e.getHp() / Math.max(1f, e.getMaxHp()), 0f, 1f);
        float originU = 0f, originV = kind == HexConstructEntity.KIND_PLATFORM ? 0f : -halfH;
        float maxDist = (float) Math.sqrt(halfW * halfW + 4 * halfH * halfH) + 0.001f;
        float aperture = Mth.lerp(partial, e.apertureOpenO, e.apertureOpen);
        Vector3f breakOrigin = e.getBreakOrigin();
        float breakAge = state == HexConstructEntity.ST_BREAKING ? Mth.clamp(stateAge / HexConstructEntity.BREAK_TICKS, 0f, 1f) : 0f;
        float dissolveAge = state == HexConstructEntity.ST_DISSOLVING ? Mth.clamp(stateAge / HexConstructEntity.DISSOLVE_TICKS, 0f, 1f) : 0f;
        float spikeGrow = switch (state) {
            case HexConstructEntity.ST_SPIKING -> HexMesh.easeOutCubic(stateAge / HexConstructEntity.SPIKE_WINDUP_TICKS);
            case HexConstructEntity.ST_FLYING -> 1f + 0.04f * Mth.sin(age * 1.3f);
            default -> 0f;
        };
        boolean flying = state == HexConstructEntity.ST_FLYING;
        boolean rising = state == HexConstructEntity.ST_RISING;
        float globalBright = 1f + (flying ? 0.35f : 0f) + (rising ? 0.3f : 0f) + (state == HexConstructEntity.ST_SPIKING ? 0.25f * spikeGrow : 0f);

        int rMax = Mth.ceil((halfH + 0.4f) / (HexMesh.CELL_R * 1.5f));
        int qMax = Mth.ceil((halfW + 0.4f) / (HexMesh.CELL_R * HexMesh.SQRT3)) + rMax / 2 + 1;
        float drawR = HexMesh.CELL_R * HexMesh.CELL_DRAW;

        for (int r = -rMax; r <= rMax; r++) {
            for (int q = -qMax; q <= qMax; q++) {
                float lu = HexMesh.gridU(q, r), lv = HexMesh.gridV(r);
                float edge = Math.min(halfW - Math.abs(lu), halfH - Math.abs(lv));
                if (edge < -0.28f) continue;
                float gate = HexHash.unit(seed, q, r, 2);
                float edgeFade = 1f;
                if (kind == HexConstructEntity.KIND_PRISON && Math.abs(lu) > Math.abs(lv) * 0.5f && edge < 0.2f) {
                    // Closing prison walls: cells along the shrinking sides fold away one by one instead
                    // of popping out, so the wall visibly gives up cells as it moves in.
                    edgeFade = HexMesh.smooth((edge + 0.28f) / 0.48f);
                    if (edgeFade <= 0.02f) continue;
                } else if (edge < 0f ? gate > 0.3f : edge < 0.26f && gate > 0.78f) continue;
                int h = HexHash.mix(seed, q, r, 1);

                // --- per-cell constants
                float ju = HexHash.signed(seed, q, r, 11) * 0.045f, jv = HexHash.signed(seed, q, r, 12) * 0.045f;
                float jn = HexHash.signed(seed, q, r, 13) * 0.05f;
                float sizeVar = 0.9f + HexHash.unit(seed, q, r, 14) * 0.14f;
                float tilt = HexHash.signed(seed, q, r, 15) * 0.14f;
                float lu2 = lu + ju, lv2 = lv + jv, ln = jn;

                // --- formation
                float du = lu2 - originU, dv = lv2 - originV;
                float dist = (float) Math.sqrt(du * du + dv * dv);
                float delay = (dist / maxDist * 0.62f + HexHash.unit(seed, q, r, 3) * 0.38f) * formTicks;
                float appear = Mth.clamp((age - delay) / Math.max(1.5f, formTicks * 0.38f), 0f, 1f);
                if (appear <= 0f) continue;
                float appearE = HexMesh.easeOutBack(appear);
                float scale = sizeVar * Math.min(1f, appearE) * edgeFade;
                float alpha = Math.min(1f, appear * 1.6f) * (0.35f + 0.65f * edgeFade);
                if (appear < 1f) {
                    float slide = (1f - HexMesh.easeOutCubic(appear));
                    float inv = dist > 0.001f ? 1f / dist : 0f;
                    lu2 += du * inv * slide * 0.5f;
                    lv2 += dv * inv * slide * 0.5f;
                    ln += (HexHash.signed(seed, q, r, 16) > 0 ? 1f : -1f) * slide * 0.55f;
                }

                // --- colour from damage, cell by cell
                float cellDamage = Mth.clamp((damage - 0.1f + HexHash.unit(seed, q, r, 4) * 0.2f) / 0.9f, 0f, 1f);
                if (damage >= 1f) cellDamage = 1f;
                HexMesh.damageColor(cellDamage, col);
                float bright = globalBright * (0.9f + 0.1f * Mth.sin(age * 0.17f + (h & 0xFF)));
                if (cellDamage > 0.66f) bright *= 0.82f + 0.18f * Mth.sin(age * 0.9f + (h & 0x3F));
                float fillAlpha = 0.16f;

                // --- ripples from hits
                float normalOffset = 0f;
                for (int i = 0; i < HexConstructEntity.MAX_HITS; i++) {
                    float hitAge = age - e.hitTick[i];
                    if (hitAge < 0f || hitAge > HIT_LIFE) continue;
                    float hu = lu2 - e.hitU[i], hv = lv2 - e.hitV[i];
                    float d = (float) Math.sqrt(hu * hu + hv * hv);
                    float wave = hitAge * 0.42f;
                    float w = (float) Math.exp(-(d - wave) * (d - wave) / 0.22f) * (1f - hitAge / HIT_LIFE) * e.hitStrength[i];
                    bright += w * 1.4f;
                    normalOffset += w * 0.16f;
                }

                // --- passage for the caster's own drill
                float apertureF = 0f;
                if (aperture > 0.001f) {
                    float au = lu2 - e.apertureU, av = lv2 - e.apertureV;
                    float d = (float) Math.sqrt(au * au + av * av);
                    float band = e.apertureR + 0.42f;
                    if (d < band) {
                        apertureF = aperture * (d < e.apertureR ? 1f : (band - d) / 0.42f);
                        float inv = d > 0.001f ? 1f / d : 0f;
                        float push = 0.5f * apertureF;
                        lu2 += au * inv * push;
                        lv2 += av * inv * push;
                        tilt += apertureF * 1.35f;
                        scale *= 1f - 0.8f * apertureF;
                        alpha *= 1f - 0.55f * apertureF;
                        bright += apertureF * 0.5f;
                    }
                }

                // --- shattering
                if (breakAge > 0f) {
                    float bu = lu2 - breakOrigin.x, bv = lv2 - breakOrigin.y;
                    float bd = (float) Math.sqrt(bu * bu + bv * bv);
                    float bDelay = bd / maxDist * 0.35f;
                    float ca = Mth.clamp((breakAge - bDelay) / (1f - bDelay), 0f, 1f);
                    if (ca <= 0f) {
                        bright += 0.6f;
                    } else {
                        float inv = bd > 0.001f ? 1f / bd : 0f;
                        float speed = 0.8f + HexHash.unit(seed, q, r, 6) * 1.3f;
                        float fly = HexMesh.easeOutCubic(ca) * speed;
                        if (bd > 0.001f) {
                            lu2 += bu * inv * fly;
                            lv2 += bv * inv * fly;
                        } else {
                            lu2 += HexHash.signed(seed, q, r, 17) * fly;
                            lv2 += HexHash.signed(seed, q, r, 18) * fly;
                        }
                        ln += (HexHash.signed(seed, q, r, 7) * 0.6f + breakOrigin.z * 0.9f) * ca * 1.3f;
                        tilt += HexHash.signed(seed, q, r, 8) * ca * 3.5f;
                        scale *= 1f - ca * 0.65f;
                        alpha *= (float) Math.pow(1f - ca, 1.3);
                        bright += 0.5f * (1f - ca);
                        fillAlpha *= 1f - ca;
                    }
                }

                // --- quiet fade-out
                if (dissolveAge > 0f) {
                    float dDelay = HexHash.unit(seed, q, r, 9) * 0.5f;
                    float ca = Mth.clamp((dissolveAge - dDelay) / (1f - dDelay), 0f, 1f);
                    scale *= 1f - ca * 0.5f;
                    alpha *= 1f - ca;
                    ln += ca * 0.3f;
                }

                // --- position, axes
                c.set(U).mul(lu2).add(tmp.set(V).mul(lv2)).add(tmp2.set(F).mul(ln + normalOffset));
                if (breakAge > 0f) c.y -= breakAge * breakAge * 1.6f; // gravity on the shards
                axes(tilt, h);
                HexMesh.cell(m, glow, fill, c, cu, cv, drawR * scale, 0.22f, col[0], col[1], col[2], bright, alpha, fillAlpha);

                // --- spikes growing out of the wall
                if (spikeGrow > 0f && edge > 0.2f && HexHash.unit(seed, q, r, 5) < 0.24f) {
                    int n = 4 + (h & 1) + ((h >>> 3) & 1);
                    for (int k = 1; k <= n; k++) {
                        float t = k / (float) (n + 1);
                        float sr = drawR * scale * (1f - t) * 0.95f;
                        float along = k * 0.27f * spikeGrow;
                        HexMesh.heatColor(t * t * 0.8f + (flying ? 0.15f : 0f), col);
                        tmp.set(F).mul(along);
                        tmp2.set(c).add(tmp);
                        HexMesh.cell(m, glow, fill, tmp2, cu, cv, sr, 0.3f, col[0], col[1], col[2], bright + 0.3f + t * 0.5f, alpha, 0.3f);
                    }
                }
                if (flying && (h & 7) == 0) {
                    // Air dragged along behind the flying wall.
                    tmp.set(c);
                    tmp2.set(F).mul(-(1.2f + HexHash.unit(seed, q, r, 19) * 1.4f)).add(c);
                    HexMesh.streak(m, glow, tmp, tmp2, U, 0.06f, 0.8f, 0.95f, 1f, 0.3f * alpha, 0f);
                }
                if (rising && (h & 3) == 0) {
                    float flick = 0.6f + 0.4f * Mth.sin(age * 1.7f + (h & 0xFF));
                    tmp.set(c);
                    tmp2.set(F).mul(-(0.8f + HexHash.unit(seed, q, r, 19) * 1.0f) * flick).add(c);
                    HexMesh.streak(m, glow, tmp, tmp2, U, 0.07f, 0.7f, 0.9f, 1f, 0.45f * alpha, 0f);
                }
            }
        }
        super.render(e, entityYaw, partial, pose, buffers, light);
    }

    /** In-plane axes with a small per-cell tilt so the cells never look like one flat sheet. */
    private void axes(float tilt, int hash) {
        float c0 = Mth.cos(tilt), s0 = Mth.sin(tilt);
        boolean aroundU = (hash & 0x100) != 0;
        if (aroundU) {
            cu.set(U);
            cv.set(V.x * c0 + F.x * s0, V.y * c0 + F.y * s0, V.z * c0 + F.z * s0);
        } else {
            cu.set(U.x * c0 + F.x * s0, U.y * c0 + F.y * s0, U.z * c0 + F.z * s0);
            cv.set(V);
        }
        // Rotate the hexagon in its own plane by a hash-based multiple of 30 degrees? Keep the grid
        // aligned: rotating individual hexagons breaks the honeycomb reading. Only the tilt varies.
    }

    private static void set(Vector3f out, Vec3 in) {
        out.set((float) in.x, (float) in.y, (float) in.z);
    }

    @Override
    public ResourceLocation getTextureLocation(HexConstructEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    @Override
    protected int getBlockLightLevel(HexConstructEntity entity, net.minecraft.core.BlockPos pos) {
        return 15;
    }
}
