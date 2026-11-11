package com.hexmagic.client.render;

import com.hexmagic.util.HexHash;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Builds the spinning honeycomb cone of a Hexa Drill. Shared by the drill, the swarm and the HUD. */
public final class HexDrillMesh {
    private HexDrillMesh() {}

    public static final int RINGS = 13;
    public static final float HELIX = 2.6f;

    private static final float[] col = new float[3];
    private static final Vector3f e1 = new Vector3f(), e2 = new Vector3f(), c = new Vector3f(), cu = new Vector3f(), cv = new Vector3f();
    private static final Vector3f radial = new Vector3f(), tmp = new Vector3f(), tmp2 = new Vector3f();

    /**
     * @param tip      cone tip position
     * @param dir      unit direction the tip points in
     * @param scale    overall size (1 = full-size drill)
     * @param spin     rotation around the axis in radians
     * @param formed   0..1: how many cells have arrived (charging animation)
     * @param breakAge 0..1 when the drill is falling apart, else 0
     * @param power    0..1 charge, drives the red heat of the tip
     * @param flow     0 = hovering, 1 = flying at full speed: adds the air stream
     */
    public static void draw(Matrix4f m, VertexConsumer glow, VertexConsumer fill, Vector3f tip, Vector3f dir, float scale, float spin,
                            float formed, float breakAge, float power, float flow, float bright, float alpha, int seed, float age) {
        if (scale <= 0.001f || alpha <= 0.004f) return;
        float length = com.hexmagic.entity.HexDrillEntity.lengthFor(scale);
        float baseR = com.hexmagic.entity.HexDrillEntity.baseRadiusFor(scale);
        e1.set(HexMesh.perpendicular(dir));
        e2.set(dir).cross(e1).normalize();
        float coneAngle = (float) Math.atan2(baseR, length);
        float ca = Mth.cos(coneAngle), sa = Mth.sin(coneAngle);
        float spacing = Mth.clamp(0.44f * scale, 0.14f, 0.5f);

        for (int k = 0; k < RINGS; k++) {
            float t = k / (float) (RINGS - 1);            // 0 = base, 1 = tip
            float ringR = baseR * (float) Math.pow(1f - t, 0.82) * (1f + 0.06f * Mth.sin(t * 9f));
            float z = t * length;
            int count = Math.max(3, Math.round(ringR * Mth.TWO_PI / spacing));
            if (t > 0.97f) count = 1;
            float cellR = Mth.clamp(spacing * 0.5f, 0.07f, 0.34f) * (0.7f + 0.3f * (1f - t));
            for (int j = 0; j < count; j++) {
                int h = HexHash.mix(seed, k, j, 1);
                float ang = j * Mth.TWO_PI / count + spin + HELIX * t + (k & 1) * (Mth.PI / count);
                float cs = Mth.cos(ang), sn = Mth.sin(ang);
                radial.set(e1).mul(cs).add(tmp.set(e2).mul(sn));

                // Arrival during charging: later rings and random cells come in last, from outside.
                float arrive = Mth.clamp((formed * 1.25f - t * 0.75f - HexHash.unit(seed, k, j, 2) * 0.3f) / 0.22f, 0f, 1f);
                if (arrive <= 0f) continue;
                float arriveE = HexMesh.easeOutBack(arrive);
                float rr = ringR + (1f - HexMesh.easeOutCubic(arrive)) * (0.9f + HexHash.unit(seed, k, j, 3) * 1.4f) * scale;
                float zz = z + (1f - HexMesh.easeOutCubic(arrive)) * HexHash.signed(seed, k, j, 4) * 0.8f * scale;
                float cellScale = Math.min(1f, arriveE);
                float a = alpha * Math.min(1f, arrive * 1.5f);
                float br = bright * (0.92f + 0.08f * Mth.sin(age * 0.6f + (h & 0xFF)));

                if (breakAge > 0f) {
                    float delay = (1f - t) * 0.3f;
                    float ba = Mth.clamp((breakAge - delay) / (1f - delay), 0f, 1f);
                    rr += HexMesh.easeOutCubic(ba) * (1.0f + HexHash.unit(seed, k, j, 5)) * scale * 2f;
                    zz -= ba * 0.6f * scale;
                    cellScale *= 1f - ba * 0.6f;
                    a *= (float) Math.pow(1f - ba, 1.2);
                    br += 0.4f * (1f - ba);
                }

                c.set(tip).sub(tmp.set(dir).mul(length - zz)).add(tmp2.set(radial).mul(rr));
                if (breakAge > 0f) c.y -= breakAge * breakAge * 1.2f;
                // Cell plane = cone surface: tangent around the axis and along the slope.
                cu.set(e1).mul(-sn).add(tmp.set(e2).mul(cs));
                cv.set(dir).mul(ca).sub(tmp.set(radial).mul(sa));
                float heat = Mth.clamp((t - 0.72f) / 0.28f, 0f, 1f) * (0.5f + 0.5f * power);
                HexMesh.heatColor(heat, col);
                HexMesh.cell(m, glow, fill, c, cu, cv, cellR * cellScale, 0.26f, col[0], col[1], col[2], br + heat * 0.6f, a, 0.22f);
            }
        }

        // Red core at the tip.
        float tipHeat = Mth.clamp(formed * 1.4f - 0.3f, 0f, 1f) * (0.35f + 0.65f * power) * (1f - breakAge);
        if (tipHeat > 0.01f) {
            float pulse = 0.85f + 0.15f * Mth.sin(age * 1.1f);
            float coreR = (0.08f + 0.16f * power) * scale * pulse;
            c.set(tip).sub(tmp.set(dir).mul(coreR * 0.6f));
            HexMesh.solidHex(m, glow, c, e1, e2, coreR * 1.2f, 1f, 0.25f, 0.15f, 0.85f * tipHeat * alpha, 0f);
            HexMesh.solidHex(m, glow, c, e2, dir, coreR * 1.2f, 1f, 0.3f, 0.15f, 0.85f * tipHeat * alpha, 0f);
            HexMesh.solidHex(m, glow, c, dir, e1, coreR * 1.2f, 1f, 0.3f, 0.15f, 0.85f * tipHeat * alpha, 0f);
            HexMesh.solidHex(m, glow, c, e1, e2, coreR * 2.4f, 1f, 0.2f, 0.1f, 0.3f * tipHeat * alpha, 0f);
        }

        // Air stream: spiralling streaks around the body, stretched backwards in flight.
        float stream = formed * (0.35f + 0.65f * power) * (1f - breakAge) * alpha;
        if (stream > 0.02f) {
            int n = 7;
            for (int i = 0; i < n; i++) {
                float ang = -spin * 1.6f + i * Mth.TWO_PI / n;
                float cs = Mth.cos(ang), sn = Mth.sin(ang);
                radial.set(e1).mul(cs).add(tmp.set(e2).mul(sn));
                float r0 = baseR * 1.25f;
                tmp2.set(tip).sub(tmp.set(dir).mul(length * (0.15f + 0.5f * (i % 3) / 3f))).add(c.set(radial).mul(r0 * 0.55f));
                Vector3f from = new Vector3f(tmp2);
                float back = length * 0.6f + flow * length * 1.9f;
                tmp2.set(from).sub(tmp.set(dir).mul(back)).add(c.set(radial).mul(r0 * 0.4f + flow * 0.5f * scale));
                cu.set(radial).cross(dir).normalize();
                HexMesh.streak(m, glow, from, tmp2, cu, 0.05f * scale + 0.03f * flow, 0.75f, 0.92f, 1f, 0.35f * stream, 0f);
            }
        }
    }
}
