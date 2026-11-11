package com.hexmagic.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player presentation options (config/hexmagic-client.toml). */
public final class HexClientConfig {
    private HexClientConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SCREEN_SHAKE = b.comment("Camera shake from Hex Staff impacts. 0 turns it off, 1 is the default strength.")
                .defineInRange("screenShake", 1.0, 0.0, 2.0);
        SHOW_HUD = b.comment("Show the ability list while the staff is held.").define("showHud", true);
        HUD_OFFSET_Y = b.comment("Vertical offset of the ability list from its default position, in GUI pixels.")
                .defineInRange("hudOffsetY", 0, -400, 400);
        SPEC = b.build();
    }
}
