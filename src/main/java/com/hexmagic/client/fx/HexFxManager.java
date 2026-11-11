package com.hexmagic.client.fx;

import com.hexmagic.client.render.HexMesh;
import com.hexmagic.client.render.HexRenderTypes;
import com.hexmagic.entity.HexConstructEntity;
import com.hexmagic.net.HexFx;
import com.hexmagic.net.HexFxPayload;
import com.hexmagic.util.HexHash;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Short-lived honeycomb effects that are not tied to a server entity: impact bursts, the reflection
 * flash, the landing cushion, gusts, targeting reticles. Ticked and drawn on the client only.
 */
public final class HexFxManager {
    private HexFxManager() {}

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final int MAX_EFFECTS = 160;
    private static final float[] col = new float[3];
    private static final Vector3f c = new Vector3f(), u = new Vector3f(), v = new Vector3f(), n = new Vector3f(), tmp = new Vector3f(), tmp2 = new Vector3f();

    public static void spawn(HexFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (p.kind() == HexFx.WALL_HIT) {
            if (level.getEntity(p.entityId()) instanceof HexConstructEntity wall) wall.addClientHit(p.pos(), p.a());
            return;
        }
        if (p.kind() == HexFx.CUSHION && p.b() > 0.5f) {
            // Landing: hand the existing cushion of this player its second phase.
            for (Effect e : EFFECTS) {
                if (e.kind == HexFx.CUSHION && e.entityId == p.entityId() && e.phase == 0) {
                    e.phase = 1;
                    e.phaseStart = e.age;
                    e.pos = p.pos();
                    return;
                }
            }
            if (p.b() > 1.5f) return; // nothing was shown: no cushion to collapse
        }
        addShake(mc, p);
        if (EFFECTS.size() >= MAX_EFFECTS) EFFECTS.remove(0);
        Effect e = new Effect(p);
        EFFECTS.add(e);
        if (e.kind == HexFx.REFLECT) {
            level.playLocalSound(e.pos.x, e.pos.y, e.pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.8f, false);
        }
    }

    // ---- camera shake: "trauma" that decays; the shake itself is trauma squared
    private static float trauma, traumaO;

    private static void addShake(Minecraft mc, HexFxPayload p) {
        if (mc.player == null) return;
        float base = switch (p.kind()) {
            case HexFx.IMPACT -> 0.12f * p.a();
            case HexFx.CRUSH -> 0.6f;
            case HexFx.LAUNCH_RING -> 0.18f + 0.25f * p.b();
            case HexFx.CUSHION -> p.b() > 0.5f ? 0.3f : 0f;
            default -> 0f;
        };
        if (base <= 0f) return;
        double dist = Math.sqrt(mc.player.distanceToSqr(p.pos()));
        float falloff = (float) Math.max(0.0, 1.0 - dist / 24.0);
        trauma = Math.min(1f, trauma + base * falloff * falloff);
    }

    /** Current shake amount 0..1 for the camera, already eased. */
    public static float shake(float partial) {
        float t = Mth.lerp(partial, traumaO, trauma);
        return t * t;
    }

    public static void clear() {
        EFFECTS.clear();
        trauma = traumaO = 0f;
    }

    public static void tick() {
        traumaO = trauma;
        trauma = Math.max(0f, trauma - 0.09f);
        Iterator<Effect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            e.age++;
            if (e.age > e.life()) it.remove();
        }
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float partial) {
        if (EFFECTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = camera.getPosition();
        Vector3f camU = new Vector3f(1, 0, 0).rotate(camera.rotation());
        Vector3f camV = new Vector3f(0, 1, 0).rotate(camera.rotation());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(HexRenderTypes.GLOW);
        VertexConsumer fill = buffers.getBuffer(HexRenderTypes.FILL);
        VertexConsumer reticle = buffers.getBuffer(HexRenderTypes.GLOW_NO_DEPTH);
        for (Effect e : EFFECTS) {
            float t = e.age - 1 + partial;
            if (t < 0) t = 0;
            switch (e.kind) {
                case HexFx.IMPACT -> impact(m, glow, fill, e, t);
                case HexFx.CRUSH -> crush(m, glow, fill, e, t, camU, camV);
                case HexFx.REFLECT -> reflect(m, glow, fill, e, t, level);
                case HexFx.CUSHION -> cushion(m, glow, fill, e, t, level);
                case HexFx.GUST -> gust(m, glow, e, t);
                case HexFx.LAUNCH_RING -> ring(m, glow, e, t);
                case HexFx.TARGET_LOCK -> lock(m, reticle, e, t, level);
                case HexFx.PRISON_PREP -> prisonPrep(m, glow, fill, e, t, level);
                default -> {}
            }
        }
        pose.popPose();
        buffers.endBatch(HexRenderTypes.FILL);
        buffers.endBatch(HexRenderTypes.GLOW);
        buffers.endBatch(HexRenderTypes.GLOW_NO_DEPTH);
    }

    // ------------------------------------------------------------------ effects

    private static void impact(Matrix4f m, VertexConsumer glow, VertexConsumer fill, Effect e, float t) {
        int count = 8 + (int) (e.a * 7);
        float life = e.life();
        float p = t / life;
        set(n, e.dir);
        if (n.lengthSquared() < 1.0e-4f) n.set(0, 1, 0);
        n.normalize();
        Vector3f side = HexMesh.perpendicular(n);
        Vector3f side2 = new Vector3f(n).cross(side);
        for (int i = 0; i < count; i++) {
            int h = HexHash.mix(e.seed, i, 0, 1);
            float ang = HexHash.unit(e.seed, i, 0, 2) * Mth.TWO_PI;
            float spread = 0.35f + HexHash.unit(e.seed, i, 0, 3) * 0.65f;
            // Shards fly back against the impact direction, fanned out.
            tmp.set(side).mul(Mth.cos(ang) * spread).add(tmp2.set(side2).mul(Mth.sin(ang) * spread)).sub(tmp2.set(n).mul(0.6f + HexHash.unit(e.seed, i, 0, 4) * 0.8f)).normalize();
            float speed = (0.14f + HexHash.unit(e.seed, i, 0, 5) * 0.22f) * (0.7f + e.a * 0.4f);
            float dist = speed * t * (1f - 0.5f * p);
            c.set((float) e.pos.x, (float) e.pos.y, (float) e.pos.z).add(tmp.mul(dist));
            c.y -= 0.012f * t * t;
            float rot = t * (0.2f + HexHash.unit(e.seed, i, 0, 6) * 0.5f);
            HexMesh.rotateAxes(side, side2, rot, u, v);
            // tilt the shard
            float tilt = HexHash.unit(e.seed, i, 0, 7) * 1.5f;
            tmp.set(u).mul(Mth.cos(tilt)).add(tmp2.set(n).mul(Mth.sin(tilt)));
            u.set(tmp);
            float r = (0.07f + HexHash.unit(e.seed, i, 0, 8) * 0.12f) * (0.8f + e.a * 0.3f) * (1f - p * 0.6f);
            HexMesh.heatColor(e.b * HexHash.unit(e.seed, i, 0, 9), col);
            HexMesh.cell(m, glow, fill, c, u, v, r, 0.3f, col[0], col[1], col[2], 1.4f * (1f - p * 0.5f), 1f - p, 0.25f);
        }
        if (t < 3f) {
            c.set((float) e.pos.x, (float) e.pos.y, (float) e.pos.z);
            float flash = (1f - t / 3f) * 0.6f;
            HexMesh.solidHex(m, glow, c, side, side2, 0.3f + e.a * 0.4f, 1f, 0.9f, 0.85f, flash, 0f);
        }
    }

    private static void crush(Matrix4f m, VertexConsumer glow, VertexConsumer fill, Effect e, float t, Vector3f camU, Vector3f camV) {
        impact(m, glow, fill, e, t);
        float p = t / e.life();
        c.set((float) e.pos.x, (float) e.pos.y, (float) e.pos.z);
        float r = e.a * (0.4f + 1.2f * HexMesh.easeOutCubic(Math.min(1f, t / 6f)));
        HexMesh.solidHex(m, glow, c, camU, camV, r, 1f, 0.85f, 0.75f, 0.7f * (1f - p), 0f);
    }

    /** A small barrier that snaps into place in front of the player, then bursts toward the shooter. */
    private static void reflect(Matrix4f m, VertexConsumer glow, VertexConsumer fill, Effect e, float t, ClientLevel level) {
        set(n, e.dir.scale(-1)); // faces the incoming projectile
        if (n.lengthSquared() < 1.0e-4f) n.set(0, 0, 1);
        n.normalize();
        Vector3f side = HexMesh.perpendicular(n);
        Vector3f up = new Vector3f(n).cross(side).normalize();
        Vec3 pos = e.pos;
        if (level.getEntity(e.entityId) instanceof Entity owner) {
            // Keep it glued to the player who reflected.
            Vec3 offset = e.pos.subtract(e.anchor);
            pos = owner.position().add(offset);
        }
        float formT = Math.min(1f, t / 2.5f);
        float breakT = Mth.clamp((t - 6f) / 9f, 0f, 1f);
        float ringR = HexMesh.CELL_R * 0.9f;
        int seed = e.seed;
        for (int r = -1; r <= 1; r++) {
            for (int q = -1; q <= 1; q++) {
                if (Math.abs(q + r) > 1) continue;
                float lu = HexMesh.gridU(q, r) * 0.95f, lv = HexMesh.gridV(r) * 0.95f;
                float d = (float) Math.sqrt(lu * lu + lv * lv);
                float appear = Mth.clamp((formT * 1.4f - d * 0.6f) / 0.5f, 0f, 1f);
                if (appear <= 0f) continue;
                float scale = HexMesh.easeOutBack(appear);
                float ln = (1f - appear) * 0.4f;
                float alpha = Math.min(1f, appear * 1.5f);
                HexMesh.heatColor(0f, col);
                float bright = 1.3f + (t < 5f ? 0.8f * (1f - t / 5f) : 0f);
                float lu2 = lu, lv2 = lv;
                u.set(side);
                v.set(up);
                if (breakT > 0f) {
                    float sp = 0.6f + HexHash.unit(seed, q, r, 1) * 1.2f;
                    float inv = d > 0.001f ? 1f / d : 0f;
                    lu2 += (d > 0.001f ? lu * inv : HexHash.signed(seed, q, r, 2)) * sp * HexMesh.easeOutCubic(breakT);
                    lv2 += (d > 0.001f ? lv * inv : HexHash.signed(seed, q, r, 3)) * sp * HexMesh.easeOutCubic(breakT);
                    ln += breakT * 2.2f; // towards the shooter
                    float rot = breakT * (1.5f + HexHash.unit(seed, q, r, 4) * 3f);
                    HexMesh.rotateAxes(side, up, rot, u, v);
                    scale *= 1f - breakT * 0.6f;
                    alpha *= 1f - breakT;
                }
                c.set((float) pos.x, (float) pos.y, (float) pos.z).add(tmp.set(side).mul(lu2)).add(tmp.set(up).mul(lv2)).add(tmp.set(n).mul(ln));
                HexMesh.cell(m, glow, fill, c, u, v, ringR * scale, 0.24f, col[0], col[1], col[2], bright, alpha, 0.2f);
            }
        }
        if (t < 4f) {
            c.set((float) pos.x, (float) pos.y, (float) pos.z);
            HexMesh.solidHex(m, glow, c, side, up, 1.0f, 0.8f, 0.95f, 1f, 0.5f * (1f - t / 4f), 0f);
        }
    }

    /** Honeycomb disc under a falling caster; phase 1 soaks up the landing. */
    private static void cushion(Matrix4f m, VertexConsumer glow, VertexConsumer fill, Effect e, float t, ClientLevel level) {
        Vector3f up = new Vector3f(0, 1, 0), ax = new Vector3f(1, 0, 0), az = new Vector3f(0, 0, 1);
        float radius = e.a;
        int seed = e.seed;
        float form = Math.min(1f, t / 6f);
        float land = e.phase == 1 ? Mth.clamp((t - e.phaseStart) / 12f, 0f, 1f) : 0f;
        int range = Mth.ceil(radius / (HexMesh.CELL_R * 1.5f)) + 1;
        for (int r = -range; r <= range; r++) {
            for (int q = -range - 1; q <= range + 1; q++) {
                float lu = HexMesh.gridU(q, r), lv = HexMesh.gridV(r);
                float d = (float) Math.sqrt(lu * lu + lv * lv);
                if (d > radius + (HexHash.unit(seed, q, r, 1) - 0.5f) * 0.5f) continue;
                float appear = Mth.clamp((form * 1.5f - d / radius * 0.7f) / 0.4f, 0f, 1f);
                if (appear <= 0f) continue;
                float scale = HexMesh.easeOutBack(appear);
                float y = (1f - appear) * -0.3f;
                float alpha = Math.min(1f, appear * 1.5f);
                float bright = 1.1f;
                float lu2 = lu, lv2 = lv;
                if (land > 0f) {
                    // Dip under the weight, then scatter outward and fade.
                    float dip = Mth.sin(Math.min(1f, land * 2f) * Mth.PI) * 0.25f * (1f - d / (radius + 0.5f));
                    y -= dip;
                    float spread = HexMesh.easeOutCubic(land) * (0.6f + HexHash.unit(seed, q, r, 2) * 0.8f);
                    float inv = d > 0.001f ? 1f / d : 0f;
                    lu2 += lu * inv * spread;
                    lv2 += lv * inv * spread;
                    alpha *= 1f - land;
                    scale *= 1f - land * 0.5f;
                    bright += (1f - land) * 0.8f;
                }
                c.set((float) e.pos.x + lu2, (float) e.pos.y + y, (float) e.pos.z + lv2);
                HexMesh.heatColor(0f, col);
                HexMesh.cell(m, glow, fill, c, ax, az, HexMesh.CELL_R * HexMesh.CELL_DRAW * scale, 0.24f, col[0], col[1], col[2], bright, alpha, 0.2f);
            }
        }
    }

    private static void gust(Matrix4f m, VertexConsumer glow, Effect e, float t) {
        float p = t / e.life();
        set(n, e.dir);
        if (n.lengthSquared() < 1.0e-4f) n.set(0, 1, 0);
        n.normalize();
        Vector3f side = HexMesh.perpendicular(n);
        Vector3f side2 = new Vector3f(n).cross(side);
        float radius = 0.3f + e.a * HexMesh.easeOutCubic(p);
        int count = 12;
        for (int i = 0; i < count; i++) {
            float ang = i * Mth.TWO_PI / count + t * 0.15f;
            float ang2 = ang + 0.35f;
            tmp.set(side).mul(Mth.cos(ang) * radius).add(tmp2.set(side2).mul(Mth.sin(ang) * radius)).add((float) e.pos.x, (float) e.pos.y, (float) e.pos.z);
            Vector3f a = new Vector3f(tmp);
            tmp.set(side).mul(Mth.cos(ang2) * radius).add(tmp2.set(side2).mul(Mth.sin(ang2) * radius)).add((float) e.pos.x, (float) e.pos.y, (float) e.pos.z);
            HexMesh.streak(m, glow, a, tmp, n, 0.05f + 0.08f * e.b, 0.8f, 0.95f, 1f, 0.45f * e.b * (1f - p), 0.1f * (1f - p));
        }
    }

    private static void ring(Matrix4f m, VertexConsumer glow, Effect e, float t) {
        float p = t / e.life();
        set(n, e.dir);
        if (n.lengthSquared() < 1.0e-4f) n.set(0, 1, 0);
        n.normalize();
        Vector3f side = HexMesh.perpendicular(n);
        Vector3f side2 = new Vector3f(n).cross(side);
        float radius = 0.4f + e.a * HexMesh.easeOutCubic(p);
        c.set((float) e.pos.x, (float) e.pos.y, (float) e.pos.z);
        HexMesh.heatColor(e.b * 0.5f, col);
        // A hexagonal ring: six thick edges.
        for (int i = 0; i < 6; i++) {
            float a0 = (float) Math.toRadians(30 + 60 * i) + t * 0.1f, a1 = a0 + (float) Math.toRadians(60);
            tmp.set(side).mul(Mth.cos(a0) * radius).add(tmp2.set(side2).mul(Mth.sin(a0) * radius)).add(c);
            Vector3f a = new Vector3f(tmp);
            tmp.set(side).mul(Mth.cos(a1) * radius).add(tmp2.set(side2).mul(Mth.sin(a1) * radius)).add(c);
            HexMesh.streak(m, glow, a, tmp, n, 0.12f, col[0], col[1], col[2], 0.8f * (1f - p), 0.8f * (1f - p));
        }
    }

    /** Three nested hexagons closing in on the target, drawn through walls. */
    private static void lock(Matrix4f m, VertexConsumer buf, Effect e, float t, ClientLevel level) {
        Entity target = level.getEntity(e.entityId);
        Vec3 pos = target != null ? target.getBoundingBox().getCenter() : e.pos;
        c.set((float) pos.x, (float) pos.y, (float) pos.z);
        float close = HexMesh.easeOutCubic(Math.min(1f, t / 12f));
        float fade = Mth.clamp((e.life() - t) / 6f, 0f, 1f);
        float size = target != null ? Math.max(target.getBbWidth(), target.getBbHeight()) * 0.6f : 0.8f;
        Vector3f camU = new Vector3f(1, 0, 0), camV = new Vector3f(0, 0, 1);
        for (int i = 0; i < 3; i++) {
            float r = (size + 0.3f * i) * (1f + (1f - close) * 2.2f);
            float spin = t * (0.08f + i * 0.04f) * (i % 2 == 0 ? 1 : -1);
            HexMesh.rotateAxes(camU, camV, spin, u, v);
            float pulse = 0.75f + 0.25f * Mth.sin(t * 0.9f + i);
            HexMesh.cell(m, buf, null, c, u, v, r, 0.08f + 0.04f * i, 1f, 0.35f, 0.3f, pulse * 1.4f, fade * 0.9f, 0f);
        }
        // Vertical ring too, so the lock reads from every angle.
        Vector3f vy = new Vector3f(0, 1, 0);
        HexMesh.rotateAxes(camU, vy, t * 0.12f, u, v);
        HexMesh.cell(m, buf, null, c, u, v, size * (1f + (1f - close) * 2.2f), 0.08f, 1f, 0.4f, 0.3f, 1.2f, fade * 0.7f, 0f);
    }

    /** Two walls beside the caster that swing shut in front of them before the prison forms. */
    private static void prisonPrep(Matrix4f m, VertexConsumer glow, VertexConsumer fill, Effect e, float t, ClientLevel level) {
        Entity owner = level.getEntity(e.entityId);
        Vec3 pos = owner != null ? owner.position() : e.pos;
        Vec3 look = owner != null ? owner.getLookAngle() : e.dir;
        Vector3f f = new Vector3f((float) look.x, 0, (float) look.z);
        if (f.lengthSquared() < 1.0e-4f) f.set(0, 0, 1);
        f.normalize();
        Vector3f right = new Vector3f(-f.z, 0, f.x);
        Vector3f up = new Vector3f(0, 1, 0);
        float total = e.a;
        float form = Math.min(1f, t / 3f);
        float swing = HexMesh.easeOutCubic(Mth.clamp((t - 2.5f) / (total - 3f), 0f, 1f));
        float fade = Mth.clamp((total + 4f - t) / 4f, 0f, 1f);
        int seed = e.seed;
        for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
            // Hinge at the caster's shoulder; the panel rotates from beside them to in front.
            float ang = sideSign * (Mth.HALF_PI - swing * (Mth.HALF_PI - 0.25f));
            Vector3f pu = new Vector3f(f).mul(Mth.cos(ang)).add(tmp.set(right).mul(Mth.sin(ang))).normalize(); // panel along-direction
            Vector3f pn = new Vector3f(pu).cross(up).normalize();
            for (int r = -2; r <= 2; r++) {
                for (int q = 0; q <= 3; q++) {
                    float lu = 0.55f + HexMesh.gridU(q, r) - r * HexMesh.CELL_R * HexMesh.SQRT3 * 0.5f;
                    float lv = 1.0f + HexMesh.gridV(r);
                    if (lu > 2.0f + HexHash.unit(seed, q, r + sideSign * 10, 1) * 0.5f) continue;
                    float appear = Mth.clamp((form * 1.5f - q * 0.25f) / 0.5f, 0f, 1f);
                    if (appear <= 0f) continue;
                    float scale = HexMesh.easeOutBack(appear);
                    c.set((float) pos.x, (float) pos.y, (float) pos.z).add(tmp.set(pu).mul(lu)).add(tmp.set(up).mul(lv)).add(tmp.set(right).mul(sideSign * 0.45f));
                    HexMesh.heatColor(0f, col);
                    HexMesh.cell(m, glow, fill, c, pu, up, HexMesh.CELL_R * HexMesh.CELL_DRAW * scale, 0.24f, col[0], col[1], col[2], 1.1f + swing * 0.4f, Math.min(1f, appear * 1.5f) * fade, 0.18f);
                }
            }
        }
    }

    private static void set(Vector3f out, Vec3 in) {
        out.set((float) in.x, (float) in.y, (float) in.z);
    }

    private static final class Effect {
        final int kind;
        Vec3 pos;
        final Vec3 anchor;
        final Vec3 dir;
        final float a, b;
        final int entityId;
        final int seed;
        int age;
        int phase;
        float phaseStart;

        Effect(HexFxPayload p) {
            kind = p.kind();
            pos = p.pos();
            dir = p.dir();
            a = p.a();
            b = p.b();
            entityId = p.entityId();
            seed = (int) (pos.x * 73856093) ^ (int) (pos.y * 19349663) ^ (int) (pos.z * 83492791) ^ (int) Minecraft.getInstance().level.getGameTime();
            Entity owner = kind == HexFx.REFLECT && Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(entityId) : null;
            anchor = owner != null ? owner.position() : pos;
        }

        int life() {
            return switch (kind) {
                case HexFx.IMPACT -> 16 + (int) (a * 4);
                case HexFx.CRUSH -> 22;
                case HexFx.REFLECT -> 16;
                case HexFx.CUSHION -> phase == 1 ? (int) phaseStart + 14 : 120;
                case HexFx.GUST -> 9;
                case HexFx.LAUNCH_RING -> 10;
                case HexFx.TARGET_LOCK -> Math.max(10, (int) a);
                case HexFx.PRISON_PREP -> (int) a + 4;
                default -> 10;
            };
        }
    }
}
