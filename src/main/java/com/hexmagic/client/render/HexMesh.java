package com.hexmagic.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Emits honeycomb cells into vertex buffers. Everything the Hex Staff draws goes through here, so
 * every construct shares one look: a bright hexagonal ring, a faint fill and a soft outer glow.
 */
public final class HexMesh {
    private HexMesh() {}

    /** Circumradius of a grid cell; cells are drawn a little smaller so gaps remain between them. */
    public static final float CELL_R = 0.36f;
    public static final float CELL_DRAW = 0.86f;
    public static final float SQRT3 = 1.7320508f;

    public static final int BASE_R = 0x8C, BASE_G = 0xDC, BASE_B = 0xFF;

    private static final float[] COS = new float[6];
    private static final float[] SIN = new float[6];

    static {
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(30 + 60 * i);
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    public static float easeOutBack(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float c = 1.70158f, c3 = c + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c * u * u;
    }

    public static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static float easeInCubic(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * t;
    }

    public static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Honeycomb colour for a damage fraction (0 = intact, 1 = destroyed) as packed floats. */
    public static void damageColor(float damage, float[] out) {
        damage = Mth.clamp(damage, 0f, 1f);
        float r, g, b;
        if (damage < 0.5f) {
            float t = smooth(damage / 0.5f);
            r = Mth.lerp(t, 0.55f, 1.0f);
            g = Mth.lerp(t, 0.86f, 0.78f);
            b = Mth.lerp(t, 1.0f, 0.25f);
        } else {
            float t = smooth((damage - 0.5f) / 0.5f);
            r = 1.0f;
            g = Mth.lerp(t, 0.78f, 0.18f);
            b = Mth.lerp(t, 0.25f, 0.12f);
        }
        out[0] = r;
        out[1] = g;
        out[2] = b;
    }

    /** Heat colour used along drills: 0 = cool blue, 1 = red tip. */
    public static void heatColor(float heat, float[] out) {
        heat = Mth.clamp(heat, 0f, 1f);
        out[0] = Mth.lerp(heat, 0.55f, 1.0f);
        out[1] = Mth.lerp(heat, 0.86f, 0.2f);
        out[2] = Mth.lerp(heat, 1.0f, 0.15f);
    }

    /**
     * One hexagonal cell at {@code c} with in-plane axes {@code u}, {@code v} (unit vectors).
     *
     * @param radius   circumradius of the ring's outer edge
     * @param ring     ring thickness as a fraction of the radius
     * @param bright   brightness multiplier of the glowing ring (1 = normal)
     * @param alpha    overall alpha
     * @param fillAlpha alpha of the inner fill (0 = hollow)
     */
    public static void cell(Matrix4f m, VertexConsumer glow, VertexConsumer fill, Vector3f c, Vector3f u, Vector3f v,
                            float radius, float ring, float r, float g, float b, float bright, float alpha, float fillAlpha) {
        if (radius <= 0.002f || alpha <= 0.004f) return;
        float ri = radius * (1f - ring);
        int cr = (int) (Mth.clamp(r * bright, 0f, 1f) * 255f);
        int cg = (int) (Mth.clamp(g * bright, 0f, 1f) * 255f);
        int cb = (int) (Mth.clamp(b * bright, 0f, 1f) * 255f);
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        // Ring.
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            vert(m, glow, c, u, v, COS[i] * radius, SIN[i] * radius, cr, cg, cb, a);
            vert(m, glow, c, u, v, COS[j] * radius, SIN[j] * radius, cr, cg, cb, a);
            vert(m, glow, c, u, v, COS[j] * ri, SIN[j] * ri, cr, cg, cb, a);
            vert(m, glow, c, u, v, COS[i] * ri, SIN[i] * ri, cr, cg, cb, a);
        }
        // Soft halo just outside the ring.
        float ro = radius * 1.18f;
        int ha = (int) (a * 0.22f);
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            vert(m, glow, c, u, v, COS[i] * radius, SIN[i] * radius, cr, cg, cb, ha);
            vert(m, glow, c, u, v, COS[j] * radius, SIN[j] * radius, cr, cg, cb, ha);
            vert(m, glow, c, u, v, COS[j] * ro, SIN[j] * ro, cr, cg, cb, 0);
            vert(m, glow, c, u, v, COS[i] * ro, SIN[i] * ro, cr, cg, cb, 0);
        }
        if (fillAlpha > 0.004f && fill != null) {
            int fa = (int) (Mth.clamp(fillAlpha * alpha, 0f, 1f) * 255f);
            int fr = (int) (Mth.clamp(r * 0.75f + 0.1f, 0f, 1f) * 255f);
            int fg = (int) (Mth.clamp(g * 0.75f + 0.1f, 0f, 1f) * 255f);
            int fb = (int) (Mth.clamp(b * 0.75f + 0.1f, 0f, 1f) * 255f);
            for (int i = 0; i < 6; i += 2) {
                int j = i + 1, k = (i + 2) % 6;
                vert(m, fill, c, u, v, 0, 0, fr, fg, fb, fa);
                vert(m, fill, c, u, v, COS[i] * ri, SIN[i] * ri, fr, fg, fb, fa);
                vert(m, fill, c, u, v, COS[j] * ri, SIN[j] * ri, fr, fg, fb, fa);
                vert(m, fill, c, u, v, COS[k] * ri, SIN[k] * ri, fr, fg, fb, fa);
            }
        }
    }

    /** Filled hexagon of one flat colour (used for flashes and glowing cores). */
    public static void solidHex(Matrix4f m, VertexConsumer buf, Vector3f c, Vector3f u, Vector3f v, float radius,
                                float r, float g, float b, float alpha, float edgeAlpha) {
        int cr = (int) (Mth.clamp(r, 0f, 1f) * 255f), cg = (int) (Mth.clamp(g, 0f, 1f) * 255f), cb = (int) (Mth.clamp(b, 0f, 1f) * 255f);
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f), ea = (int) (Mth.clamp(edgeAlpha, 0f, 1f) * 255f);
        for (int i = 0; i < 6; i += 2) {
            int j = i + 1, k = (i + 2) % 6;
            vert(m, buf, c, u, v, 0, 0, cr, cg, cb, a);
            vert(m, buf, c, u, v, COS[i] * radius, SIN[i] * radius, cr, cg, cb, ea);
            vert(m, buf, c, u, v, COS[j] * radius, SIN[j] * radius, cr, cg, cb, ea);
            vert(m, buf, c, u, v, COS[k] * radius, SIN[k] * radius, cr, cg, cb, ea);
        }
    }

    /** Thin glowing strip from a to b with the given half width perpendicular to {@code side}. */
    public static void streak(Matrix4f m, VertexConsumer buf, Vector3f a, Vector3f b, Vector3f side, float halfWidth,
                              float r, float g, float bl, float alphaA, float alphaB) {
        int cr = (int) (Mth.clamp(r, 0f, 1f) * 255f), cg = (int) (Mth.clamp(g, 0f, 1f) * 255f), cb = (int) (Mth.clamp(bl, 0f, 1f) * 255f);
        int aa = (int) (Mth.clamp(alphaA, 0f, 1f) * 255f), ab = (int) (Mth.clamp(alphaB, 0f, 1f) * 255f);
        float sx = side.x * halfWidth, sy = side.y * halfWidth, sz = side.z * halfWidth;
        buf.addVertex(m, a.x - sx, a.y - sy, a.z - sz).setColor(cr, cg, cb, 0);
        buf.addVertex(m, a.x, a.y, a.z).setColor(cr, cg, cb, aa);
        buf.addVertex(m, b.x, b.y, b.z).setColor(cr, cg, cb, ab);
        buf.addVertex(m, b.x - sx, b.y - sy, b.z - sz).setColor(cr, cg, cb, 0);
        buf.addVertex(m, a.x, a.y, a.z).setColor(cr, cg, cb, aa);
        buf.addVertex(m, a.x + sx, a.y + sy, a.z + sz).setColor(cr, cg, cb, 0);
        buf.addVertex(m, b.x + sx, b.y + sy, b.z + sz).setColor(cr, cg, cb, 0);
        buf.addVertex(m, b.x, b.y, b.z).setColor(cr, cg, cb, ab);
    }

    /** Camera-facing soft glow disc (approximated by a hexagon fading to its edge). */
    public static void glowSprite(Matrix4f m, VertexConsumer buf, Vector3f c, Vector3f camU, Vector3f camV, float radius,
                                  float r, float g, float b, float alpha) {
        solidHex(m, buf, c, camU, camV, radius, r, g, b, alpha, 0f);
    }

    private static void vert(Matrix4f m, VertexConsumer buf, Vector3f c, Vector3f u, Vector3f v, float lu, float lv, int r, int g, int b, int a) {
        buf.addVertex(m, c.x + u.x * lu + v.x * lv, c.y + u.y * lu + v.y * lv, c.z + u.z * lu + v.z * lv).setColor(r, g, b, a);
    }

    /** Axial grid coordinates -> local plane position for pointy-top hexagons. */
    public static float gridU(int q, int r) {
        return CELL_R * SQRT3 * (q + r * 0.5f);
    }

    public static float gridV(int r) {
        return CELL_R * 1.5f * r;
    }

    /** Rotates (x, y) in the plane of u/v by angle, writing the rotated axes into outU/outV. */
    public static void rotateAxes(Vector3f u, Vector3f v, float angle, Vector3f outU, Vector3f outV) {
        float c = Mth.cos(angle), s = Mth.sin(angle);
        outU.set(u.x * c + v.x * s, u.y * c + v.y * s, u.z * c + v.z * s);
        outV.set(-u.x * s + v.x * c, -u.y * s + v.y * c, -u.z * s + v.z * c);
    }

    /** Any unit vector perpendicular to n. */
    public static Vector3f perpendicular(Vector3f n) {
        Vector3f a = Math.abs(n.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        return a.cross(n).normalize();
    }

    public static Vector3f jitter(int hash, float amount) {
        float x = ((hash & 0xFF) / 255f - 0.5f) * amount;
        float y = (((hash >>> 8) & 0xFF) / 255f - 0.5f) * amount;
        float z = (((hash >>> 16) & 0xFF) / 255f - 0.5f) * amount;
        return new Vector3f(x, y, z);
    }

    public static void applyPose(PoseStack pose, Vector3f out, float x, float y, float z) {
        out.set(x, y, z);
    }
}
