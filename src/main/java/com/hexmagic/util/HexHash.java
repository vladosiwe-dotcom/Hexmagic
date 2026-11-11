package com.hexmagic.util;

/** Cheap deterministic per-cell randomness shared by every honeycomb construct. */
public final class HexHash {
    private HexHash() {}

    public static int mix(int seed, int a, int b, int salt) {
        int h = seed * 0x9E3779B1 + a * 0x85EBCA6B + b * 0xC2B2AE35 + salt * 0x27D4EB2F;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    /** Uniform value in [0, 1). */
    public static float unit(int seed, int a, int b, int salt) {
        return (mix(seed, a, b, salt) >>> 8) / (float) (1 << 24);
    }

    /** Uniform value in [-1, 1). */
    public static float signed(int seed, int a, int b, int salt) {
        return unit(seed, a, b, salt) * 2f - 1f;
    }
}
