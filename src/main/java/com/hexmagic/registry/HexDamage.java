package com.hexmagic.registry;

import com.hexmagic.HexMagic;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class HexDamage {
    private HexDamage() {}

    public static final ResourceKey<DamageType> DRILL = key("hex_drill");
    public static final ResourceKey<DamageType> SPIKE = key("hex_spike");
    /** Pure damage of the closing Hex Prison: ignores armor, enchantments and resistance. */
    public static final ResourceKey<DamageType> CRUSH = key("hex_crush");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, HexMagic.id(name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, owner);
    }

    public static boolean isHexDamage(DamageSource source) {
        return source.is(DRILL) || source.is(SPIKE) || source.is(CRUSH);
    }
}
