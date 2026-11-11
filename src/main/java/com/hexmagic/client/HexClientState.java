package com.hexmagic.client;

import com.hexmagic.ability.HexAbility;
import com.hexmagic.compat.IronsCompat;
import com.hexmagic.net.SelectAbilityPayload;
import com.hexmagic.net.SyncStatePayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Mirror of the player's Hex Staff state for the HUD and for choosing what the mouse casts. */
public final class HexClientState {
    private HexClientState() {}

    public static int charges, maxCharges = 3, chargeRegen, chargeRegenMax = 140;
    public static final int[] cooldown = new int[HexAbility.COUNT];
    public static final int[] cooldownMax = new int[HexAbility.COUNT];
    public static int passivesDisabled;
    public static int selected;
    public static int idleTicks, idleMax = 700;
    public static boolean swarmReady, onPlatform;
    /** Id of the local player's charging drill, or -1. */
    public static int chargingDrill = -1;
    public static float hudSelectAnim;
    public static int lastSelectChange;

    public static void accept(SyncStatePayload p) {
        charges = p.charges();
        maxCharges = p.maxCharges();
        chargeRegen = p.chargeRegen();
        chargeRegenMax = p.chargeRegenMax();
        System.arraycopy(p.cooldown(), 0, cooldown, 0, HexAbility.COUNT);
        System.arraycopy(p.cooldownMax(), 0, cooldownMax, 0, HexAbility.COUNT);
        passivesDisabled = p.passivesDisabled();
        idleTicks = p.idleTicks();
        idleMax = p.idleMax();
        swarmReady = p.swarmReady();
        onPlatform = p.onPlatform();
        if (p.selected() != selected) selected = p.selected();
    }

    /** Local countdown between server syncs so bars move smoothly. */
    public static void tick() {
        for (int i = 0; i < HexAbility.COUNT; i++) if (cooldown[i] > 0) cooldown[i]--;
        if (passivesDisabled > 0) passivesDisabled--;
        if (charges < maxCharges) chargeRegen++;
        idleTicks++;
    }

    public static void select(int index) {
        if (!HexAbility.isValidSelection(index, IronsCompat.LOADED)) return;
        if (index == selected) return;
        selected = index;
        lastSelectChange = 0;
        PacketDistributor.sendToServer(new SelectAbilityPayload(index));
    }

    public static void cycle(int step) {
        select(HexAbility.cycle(selected, step, IronsCompat.LOADED));
    }

    public static void reset() {
        charges = maxCharges;
        java.util.Arrays.fill(cooldown, 0);
        passivesDisabled = 0;
        chargingDrill = -1;
        swarmReady = false;
        onPlatform = false;
    }
}
