package com.hexmagic.client;

import com.hexmagic.ability.HexAbility;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Every binding is changeable in the vanilla Controls screen under the "Hex Staff" category. */
public final class HexKeys {
    private HexKeys() {}

    public static final String CATEGORY = "key.categories.hexmagic";

    public static final KeyMapping SHIELD = key("shield", GLFW.GLFW_KEY_G);
    public static final KeyMapping DRILL = key("drill", GLFW.GLFW_KEY_V);
    public static final KeyMapping SPIKE = key("spike", GLFW.GLFW_KEY_B);
    public static final KeyMapping LAUNCH = key("launch", GLFW.GLFW_KEY_C);
    public static final KeyMapping HEX_ATTACK = key("hex_attack", GLFW.GLFW_KEY_X);
    public static final KeyMapping PRISON = key("prison", GLFW.GLFW_KEY_Z);
    public static final KeyMapping NEXT = key("next", GLFW.GLFW_KEY_R);
    public static final KeyMapping PREV = key("prev", InputConstants.UNKNOWN.getValue());

    public static final KeyMapping[] ABILITY_KEYS = new KeyMapping[HexAbility.COUNT];

    static {
        ABILITY_KEYS[HexAbility.SHIELD.ordinal()] = SHIELD;
        ABILITY_KEYS[HexAbility.DRILL.ordinal()] = DRILL;
        ABILITY_KEYS[HexAbility.SPIKE.ordinal()] = SPIKE;
        ABILITY_KEYS[HexAbility.LAUNCH.ordinal()] = LAUNCH;
        ABILITY_KEYS[HexAbility.HEX_ATTACK.ordinal()] = HEX_ATTACK;
        ABILITY_KEYS[HexAbility.PRISON.ordinal()] = PRISON;
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.hexmagic." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static KeyMapping[] all() {
        return new KeyMapping[] {SHIELD, DRILL, SPIKE, LAUNCH, HEX_ATTACK, PRISON, NEXT, PREV};
    }
}
