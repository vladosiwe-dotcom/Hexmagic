package com.hexmagic.util;

import net.minecraft.world.phys.Vec3;

/**
 * Orientation of a flat honeycomb panel. {@code f} is the panel normal (the direction a player
 * with this yaw/pitch looks in), {@code u} runs along the panel to the right of that player and
 * {@code v} runs "up" along the panel.
 */
public record HexBasis(Vec3 f, Vec3 u, Vec3 v) {
    private static final double DEG = Math.PI / 180.0;

    public static HexBasis of(float yaw, float pitch) {
        double yr = yaw * DEG, pr = pitch * DEG;
        double cy = Math.cos(yr), sy = Math.sin(yr), cp = Math.cos(pr), sp = Math.sin(pr);
        Vec3 f = new Vec3(-sy * cp, -sp, cy * cp);
        Vec3 u = new Vec3(-cy, 0, -sy);
        Vec3 v = u.cross(f);
        return new HexBasis(f, u, v);
    }

    public Vec3 toWorld(Vec3 center, double lu, double lv, double ln) {
        return new Vec3(
                center.x + u.x * lu + v.x * lv + f.x * ln,
                center.y + u.y * lu + v.y * lv + f.y * ln,
                center.z + u.z * lu + v.z * lv + f.z * ln);
    }

    /** Yaw (degrees) of a horizontal direction, matching {@link #of}. */
    public static float yawOf(double x, double z) {
        return (float) (Math.atan2(-x, z) / DEG);
    }

    public static float pitchOf(Vec3 dir) {
        double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return (float) (-Math.atan2(dir.y, h) / DEG);
    }
}
