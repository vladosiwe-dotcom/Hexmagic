package com.hexmagic.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/** Per-player Hex Staff state. Only the timers survive a relog; links to live entities do not. */
public class HexPlayerData {
    public static final Codec<HexPlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("charges").forGetter(d -> d.charges),
            Codec.INT.fieldOf("charge_regen").forGetter(d -> d.chargeRegen),
            Codec.INT.listOf().fieldOf("cooldowns").forGetter(HexPlayerData::cooldownList),
            Codec.INT.fieldOf("passives_disabled").forGetter(d -> d.passivesDisabled),
            Codec.INT.fieldOf("selected").forGetter(d -> d.selected)
    ).apply(i, HexPlayerData::new));

    public int charges = -1;
    public int chargeRegen;
    public final int[] cooldown = new int[HexAbility.COUNT];
    public final int[] cooldownMax = new int[HexAbility.COUNT];
    public int passivesDisabled;
    public int selected;
    public int idleTicks;

    // ---- transient links and bookkeeping (server only) ----
    public int drillId = -1;
    public int swarmId = -1;
    public int prisonId = -1;
    public int platformId = -1;
    public boolean onPlatform;
    public int safeLandingTicks;
    public boolean cushionShown;
    public double lastY = Double.NaN;
    public long lastPressTick = -1;
    public long lastUseTick = -100;
    public int staffAwayTicks;
    public boolean dirty = true;

    public HexPlayerData() {}

    private HexPlayerData(int charges, int chargeRegen, List<Integer> cooldowns, int passivesDisabled, int selected) {
        this.charges = charges;
        this.chargeRegen = chargeRegen;
        for (int i = 0; i < cooldown.length && i < cooldowns.size(); i++) {
            cooldown[i] = Math.max(0, cooldowns.get(i));
            cooldownMax[i] = cooldown[i];
        }
        this.passivesDisabled = Math.max(0, passivesDisabled);
        this.selected = selected;
    }

    private List<Integer> cooldownList() {
        List<Integer> l = new ArrayList<>(cooldown.length);
        for (int c : cooldown) l.add(c);
        return l;
    }

    public boolean passivesEnabled() {
        return passivesDisabled <= 0;
    }

    public boolean ready(HexAbility ability) {
        return cooldown[ability.ordinal()] <= 0;
    }

    public void startCooldown(HexAbility ability, int ticks) {
        cooldown[ability.ordinal()] = ticks;
        cooldownMax[ability.ordinal()] = ticks;
        dirty = true;
    }
}
