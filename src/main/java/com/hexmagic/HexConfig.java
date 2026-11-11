package com.hexmagic;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side balance values. Everything the design document fixed is the default here; values the
 * document left open are marked PROVISIONAL in their comment so they are easy to find and retune.
 */
public final class HexConfig {
    private HexConfig() {}

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_CHARGES;
    public static final ModConfigSpec.IntValue CHARGE_RECHARGE_TICKS;
    public static final ModConfigSpec.DoubleValue CONSTRUCT_HP;
    public static final ModConfigSpec.IntValue PASSIVE_LOCKOUT_TICKS;
    public static final ModConfigSpec.BooleanValue OWNER_PASSES_THROUGH;

    public static final ModConfigSpec.IntValue SHIELD_LIFETIME_TICKS;
    public static final ModConfigSpec.DoubleValue SHIELD_WIDTH;
    public static final ModConfigSpec.DoubleValue SHIELD_HEIGHT;

    public static final ModConfigSpec.IntValue DRILL_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue DRILL_HIT_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue DRILL_CONTACT_HITS;
    public static final ModConfigSpec.DoubleValue DRILL_SPEED;
    public static final ModConfigSpec.DoubleValue DRILL_RANGE;
    public static final ModConfigSpec.BooleanValue DRILL_BREAKS_BLOCKS;
    public static final ModConfigSpec.BooleanValue DRILL_DROPS_BLOCKS;
    public static final ModConfigSpec.DoubleValue DRILL_MAX_HARDNESS;

    public static final ModConfigSpec.DoubleValue SPIKE_DAMAGE;
    public static final ModConfigSpec.IntValue SPIKE_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue SPIKE_KNOCKBACK;

    public static final ModConfigSpec.DoubleValue LAUNCH_HEIGHT;
    public static final ModConfigSpec.IntValue PLATFORM_LIFETIME_TICKS;
    public static final ModConfigSpec.DoubleValue PLATFORM_SIZE;
    public static final ModConfigSpec.DoubleValue PLATFORM_CAST_BOOST;

    public static final ModConfigSpec.IntValue REFLECT_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue REFLECT_DAMAGE_FACTOR;

    public static final ModConfigSpec.IntValue HEX_ATTACK_IDLE_TICKS;
    public static final ModConfigSpec.IntValue HEX_ATTACK_DRILLS;
    public static final ModConfigSpec.DoubleValue HEX_ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue HEX_ATTACK_RANGE;
    public static final ModConfigSpec.IntValue HEX_ATTACK_COOLDOWN_TICKS;

    public static final ModConfigSpec.IntValue PRISON_DURATION_TICKS;
    public static final ModConfigSpec.IntValue PRISON_CLOSE_START_TICKS;
    public static final ModConfigSpec.DoubleValue PRISON_DAMAGE;
    public static final ModConfigSpec.DoubleValue PRISON_RANGE;
    public static final ModConfigSpec.IntValue PRISON_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue PRISON_CHARGE_COST;

    public static final ModConfigSpec.BooleanValue IRONS_SCALE_WITH_SPELL_POWER;
    public static final ModConfigSpec.BooleanValue IRONS_APPLY_COOLDOWN_REDUCTION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        MAX_CHARGES = b.comment("Barrier charges shared by Hexa Shield, Hexa Launch and a fresh Hexa Spike wall.")
                .defineInRange("maxCharges", 3, 1, 10);
        CHARGE_RECHARGE_TICKS = b.comment("Ticks to restore one barrier charge (7 seconds).")
                .defineInRange("chargeRechargeTicks", 140, 1, 12000);
        CONSTRUCT_HP = b.comment("Durability of every honeycomb construct. Each construct tracks its own value.")
                .defineInRange("constructHp", 20.0, 1.0, 1000.0);
        PASSIVE_LOCKOUT_TICKS = b.comment("How long passives stay disabled after a protective field is destroyed (30 seconds).")
                .defineInRange("passiveLockoutTicks", 600, 0, 12000);
        OWNER_PASSES_THROUGH = b.comment("Whether the caster and their allies can walk through their own barriers.")
                .define("ownerPassesThroughBarriers", true);
        b.pop();

        b.push("hexaShield");
        SHIELD_LIFETIME_TICKS = b.comment("PROVISIONAL: lifetime of an undamaged Hexa Shield wall.")
                .defineInRange("lifetimeTicks", 600, 20, 72000);
        SHIELD_WIDTH = b.defineInRange("width", 4.4, 1.0, 12.0);
        SHIELD_HEIGHT = b.defineInRange("height", 3.5, 1.0, 12.0);
        b.pop();

        b.push("hexaDrill");
        DRILL_COOLDOWN_TICKS = b.comment("Cooldown after the drill is released (5 seconds).")
                .defineInRange("cooldownTicks", 100, 0, 12000);
        DRILL_HIT_INTERVAL_TICKS = b.comment("Interval between damage ticks while the drill stays in contact (0.5 seconds).")
                .defineInRange("hitIntervalTicks", 10, 1, 200);
        DRILL_CONTACT_HITS = b.comment("PROVISIONAL: how many damage ticks the drill deals to one target before it punches through.")
                .defineInRange("contactHits", 2, 1, 20);
        DRILL_SPEED = b.comment("Flight speed in blocks per tick.").defineInRange("speed", 2.4, 0.5, 3.8);
        DRILL_RANGE = b.defineInRange("range", 48.0, 4.0, 160.0);
        DRILL_BREAKS_BLOCKS = b.define("breaksBlocks", true);
        DRILL_DROPS_BLOCKS = b.define("dropsBlocks", true);
        DRILL_MAX_HARDNESS = b.comment("Hardest block a fully charged drill can bore through (stone 1.5, ores 3, iron block 5, obsidian 50).")
                .defineInRange("maxHardness", 6.0, 0.0, 100.0);
        b.pop();

        b.push("hexaSpike");
        SPIKE_DAMAGE = b.comment("PROVISIONAL: damage of the spiked wall. Not a final balance value.")
                .defineInRange("damage", 7.0, 0.0, 1000.0);
        SPIKE_COOLDOWN_TICKS = b.comment("PROVISIONAL").defineInRange("cooldownTicks", 120, 0, 12000);
        SPIKE_KNOCKBACK = b.comment("PROVISIONAL").defineInRange("knockback", 0.9, 0.0, 5.0);
        b.pop();

        b.push("hexaLaunch");
        LAUNCH_HEIGHT = b.defineInRange("height", 15.0, 2.0, 64.0);
        PLATFORM_LIFETIME_TICKS = b.defineInRange("platformLifetimeTicks", 400, 20, 12000);
        PLATFORM_SIZE = b.defineInRange("platformSize", 4.0, 1.0, 12.0);
        PLATFORM_CAST_BOOST = b.comment("PROVISIONAL: cast speed multiplier for Hexa Shield and Hexa Drill while standing on the platform.")
                .defineInRange("castBoost", 1.5, 1.0, 5.0);
        b.pop();

        b.push("hexaReflection");
        REFLECT_COOLDOWN_TICKS = b.defineInRange("cooldownTicks", 300, 0, 12000);
        REFLECT_DAMAGE_FACTOR = b.comment("Share of the original damage a reflected projectile deals (2/3).")
                .defineInRange("damageFactor", 2.0 / 3.0, 0.0, 10.0);
        b.pop();

        b.push("hexAttack");
        HEX_ATTACK_IDLE_TICKS = b.comment("Ticks without casting before the small drills fire on their own (35 seconds).")
                .defineInRange("idleTicks", 700, 20, 72000);
        HEX_ATTACK_DRILLS = b.comment("PROVISIONAL: number of small drills.").defineInRange("drills", 5, 1, 12);
        HEX_ATTACK_DAMAGE = b.defineInRange("damage", 5.0, 0.0, 1000.0);
        HEX_ATTACK_RANGE = b.comment("PROVISIONAL: targeting range.").defineInRange("range", 24.0, 4.0, 64.0);
        HEX_ATTACK_COOLDOWN_TICKS = b.comment("PROVISIONAL: cooldown of a manual summon.")
                .defineInRange("cooldownTicks", 300, 0, 12000);
        b.pop();

        b.push("hexPrison");
        PRISON_DURATION_TICKS = b.defineInRange("durationTicks", 200, 40, 12000);
        PRISON_CLOSE_START_TICKS = b.comment("Tick at which the walls start closing in (7th second).")
                .defineInRange("closeStartTicks", 140, 10, 12000);
        PRISON_DAMAGE = b.comment("Pure damage dealt when the walls meet.").defineInRange("damage", 10.0, 0.0, 1000.0);
        PRISON_RANGE = b.comment("PROVISIONAL").defineInRange("range", 24.0, 4.0, 64.0);
        PRISON_COOLDOWN_TICKS = b.comment("PROVISIONAL").defineInRange("cooldownTicks", 900, 0, 72000);
        PRISON_CHARGE_COST = b.comment("Barrier charges spent on the two preparation walls.").defineInRange("chargeCost", 2, 0, 10);
        b.pop();

        b.push("ironsSpellbooks");
        IRONS_SCALE_WITH_SPELL_POWER = b.comment("Multiply Hex Staff damage by the caster's Iron's Spells 'n Spellbooks spell power.",
                        "Off by default so the documented damage numbers stay exact.")
                .define("scaleDamageWithSpellPower", false);
        IRONS_APPLY_COOLDOWN_REDUCTION = b.comment("Let Iron's Spells cooldown reduction shorten Hex Staff cooldowns.")
                .define("applyCooldownReduction", false);
        b.pop();

        SPEC = b.build();
    }
}
