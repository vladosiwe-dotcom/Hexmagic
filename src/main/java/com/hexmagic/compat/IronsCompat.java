package com.hexmagic.compat;

import com.hexmagic.HexConfig;
import com.hexmagic.compat.irons.IronsHooks;
import com.hexmagic.item.HexStaffItem;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * The only doorway to Iron's Spells 'n Spellbooks. Nothing outside {@code compat.irons} touches its
 * classes, and that package is only reached when the mod is actually installed, so the Hex Staff
 * loads fine on its own.
 */
public final class IronsCompat {
    private IronsCompat() {}

    public static final String MOD_ID = "irons_spellbooks";
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded(MOD_ID);

    public static Item createStaff(Item.Properties properties) {
        return LOADED ? IronsHooks.createStaff(properties) : new HexStaffItem(properties);
    }

    /** Multiplier for Hex Staff damage from the caster's spell power; 1 unless enabled in the config. */
    public static float damageMultiplier(@Nullable Entity caster) {
        if (!LOADED || caster == null || !HexConfig.IRONS_SCALE_WITH_SPELL_POWER.get()) return 1f;
        return IronsHooks.spellPower(caster);
    }

    /** Multiplier for Hex Staff cooldowns from Iron's cooldown reduction; 1 unless enabled. */
    public static float cooldownMultiplier(@Nullable Entity caster) {
        if (!LOADED || caster == null || !HexConfig.IRONS_APPLY_COOLDOWN_REDUCTION.get()) return 1f;
        return IronsHooks.cooldownMultiplier(caster);
    }

    /** Damage carried by an Iron's spell projectile, or -1 when it is not one. */
    public static float projectileDamage(Projectile projectile) {
        return LOADED ? IronsHooks.projectileDamage(projectile) : -1f;
    }

    /**
     * Lets an Iron's spell projectile be reflected properly. Returns true when the damage factor was
     * already applied to the projectile itself.
     */
    public static boolean onReflect(Projectile projectile, @Nullable Entity originalShooter, float damageFactor) {
        return LOADED && IronsHooks.onReflect(projectile, originalShooter, damageFactor);
    }
}
