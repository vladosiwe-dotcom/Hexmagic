package com.hexmagic.compat.irons;

import io.redspace.ironsspellbooks.api.item.weapons.ExtendedSwordItem;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.item.weapons.StaffTier;
import com.hexmagic.item.HexStaffLogic;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;

/** Everything that references Iron's Spells 'n Spellbooks classes. Only loaded when it is present. */
public final class IronsHooks {
    private IronsHooks() {}

    /** The Hex Staff as an Iron's staff: melee stats plus the usual staff bonuses. */
    private static final StaffTier HEX_TIER = new StaffTier(
            HexStaffLogic.ATTACK_DAMAGE,
            HexStaffLogic.ATTACK_SPEED,
            new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new AttributeContainer(AttributeRegistry.MANA_REGEN, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

    public static Item createStaff(Item.Properties properties) {
        return new IronsHexStaffItem(properties.attributes(ExtendedSwordItem.createAttributes(HEX_TIER)));
    }

    public static float spellPower(Entity caster) {
        if (!(caster instanceof LivingEntity living) || living.getAttribute(AttributeRegistry.SPELL_POWER) == null) return 1f;
        return (float) Mth.clamp(living.getAttributeValue(AttributeRegistry.SPELL_POWER), 0.1, 10.0);
    }

    /** Same curve Iron's uses for its own spells. */
    public static float cooldownMultiplier(Entity caster) {
        if (!(caster instanceof LivingEntity living) || living.getAttribute(AttributeRegistry.COOLDOWN_REDUCTION) == null) return 1f;
        double modifier = living.getAttributeValue(AttributeRegistry.COOLDOWN_REDUCTION);
        return (float) Mth.clamp(2.0 - Utils.softCapFormula(modifier), 0.1, 2.0);
    }

    public static float projectileDamage(Projectile projectile) {
        return projectile instanceof AbstractMagicProjectile magic ? magic.getDamage() : -1f;
    }

    public static boolean onReflect(Projectile projectile, @Nullable Entity originalShooter, float damageFactor) {
        if (!(projectile instanceof AbstractMagicProjectile magic)) return false;
        magic.setDamage(magic.getDamage() * damageFactor);
        magic.setExplosionRadius(magic.getExplosionRadius());
        // A reflected spell goes back to whoever cast it, the same way its own homing would work.
        if (originalShooter instanceof LivingEntity living && living.isAlive()) {
            magic.setHomingTarget(living);
        } else {
            magic.stopEntityHoming();
            magic.setCursorHoming(false);
        }
        return true;
    }
}
