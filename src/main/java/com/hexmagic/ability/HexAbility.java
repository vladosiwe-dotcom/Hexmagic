package com.hexmagic.ability;

import net.minecraft.network.chat.Component;

/** The seven abilities of the Hex Staff, in the order the design document numbers them. */
public enum HexAbility {
    SHIELD("shield", Kind.ACTIVE),
    DRILL("drill", Kind.ACTIVE),
    SPIKE("spike", Kind.ACTIVE),
    LAUNCH("launch", Kind.PASSIVE),
    REFLECTION("reflection", Kind.PASSIVE),
    HEX_ATTACK("hex_attack", Kind.PASSIVE),
    PRISON("prison", Kind.ULTIMATE);

    public enum Kind { ACTIVE, PASSIVE, ULTIMATE }

    public static final HexAbility[] VALUES = values();
    public static final int COUNT = VALUES.length;
    /** Selection index that hands the right mouse button over to Iron's Spells 'n Spellbooks. */
    public static final int SELECT_IRONS = COUNT;
    /** Abilities reachable with the "use" button, in cycling order. Reflection is fully automatic. */
    private static final int[] CYCLE = {0, 1, 2, 3, 5, 6};

    public final String id;
    public final Kind kind;

    HexAbility(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public boolean isPassive() {
        return kind == Kind.PASSIVE;
    }

    public Component displayName() {
        return Component.translatable("ability.hexmagic." + id);
    }

    public static HexAbility byIndex(int i) {
        return i >= 0 && i < COUNT ? VALUES[i] : null;
    }

    public static boolean isValidSelection(int index, boolean ironsLoaded) {
        if (index == SELECT_IRONS) return ironsLoaded;
        for (int c : CYCLE) if (c == index) return true;
        return false;
    }

    public static int cycle(int current, int step, boolean ironsLoaded) {
        int n = CYCLE.length + (ironsLoaded ? 1 : 0);
        int pos = 0;
        if (current == SELECT_IRONS) pos = CYCLE.length;
        else for (int i = 0; i < CYCLE.length; i++) if (CYCLE[i] == current) pos = i;
        pos = Math.floorMod(pos + step, n);
        return pos == CYCLE.length ? SELECT_IRONS : CYCLE[pos];
    }
}
