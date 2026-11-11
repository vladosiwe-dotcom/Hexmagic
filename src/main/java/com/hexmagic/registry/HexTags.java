package com.hexmagic.registry;

import com.hexmagic.HexMagic;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public final class HexTags {
    private HexTags() {}

    /** Blocks the Hexa Drill can never bore through, whatever their hardness. */
    public static final TagKey<Block> DRILL_IMMUNE = TagKey.create(Registries.BLOCK, HexMagic.id("drill_immune"));
    /** Projectiles Hexa Reflection leaves alone (pearls, fishing bobbers and the like). */
    public static final TagKey<EntityType<?>> NOT_REFLECTABLE = TagKey.create(Registries.ENTITY_TYPE, HexMagic.id("not_reflectable"));
    /** Entities the automatic Hex Attack never picks, even when they would count as hostile. */
    public static final TagKey<EntityType<?>> HEX_ATTACK_IGNORED = TagKey.create(Registries.ENTITY_TYPE, HexMagic.id("hex_attack_ignored"));
}
